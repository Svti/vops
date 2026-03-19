#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# -----------------------------------------------------------------------------
# Control Script for the APP Server
#
# Environment Variable Prerequisites
#
#   Do not set the variables in this script. Instead put them into a script
#   setenv.sh in APP_BASE/bin to keep your customizations separate.
#
#   APP_HOME   May point at your Catalina "build" directory.
#
#
#   APP_OUT    (Optional) Full path to a file where stdout and stderr
#                   will be redirected.
#                   Default is $APP_BASE/logs/catalina.out
#
#   APP_OPTS   (Optional) Java runtime options used when the "start",
#                   "run" or "debug" command is executed.
#                   Include here and not in JAVA_OPTS all options, that should
#                   only be used by Tomcat itself, not by the stop process,
#                   the version command etc.
#                   Examples are heap size, GC logging, JMX ports etc.
#
#   APP_TMPDIR (Optional) Directory path location of temporary directory
#                   the JVM should use (java.io.tmpdir).  Defaults to
#                   $APP_BASE/temp.
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#                   Required to run the with the "debug" argument.
#
#   JRE_HOME        Must point at your Java Runtime installation.
#                   Defaults to JAVA_HOME if empty. If JRE_HOME and JAVA_HOME
#                   are both set, JRE_HOME is used.
#
#   JAVA_OPTS       (Optional) Java runtime options used when any command
#                   is executed.
#                   Include here and not in APP_OPTS all options, that
#                   should be used by Tomcat and also by the stop process,
#                   the version command etc.
#                   Most options should go into APP_OPTS.
#
#   JAVA_ENDORSED_DIRS (Optional) Lists of of colon separated directories
#                   containing some jars in order to allow replacement of APIs
#                   created outside of the JCP (i.e. DOM and SAX from W3C).
#                   It can also be used to update the XML parser implementation.
#                   Defaults to $APP_HOME/endorsed.
#
#   JPDA_TRANSPORT  (Optional) JPDA transport used when the "jpda start"
#                   command is executed. The default is "dt_socket".
#
#   JPDA_ADDRESS    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. The default is localhost:8000.
#
#   JPDA_SUSPEND    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. Specifies whether JVM should suspend
#                   execution immediately after startup. Default is "n".
#
#   JPDA_OPTS       (Optional) Java runtime options used when the "jpda start"
#                   command is executed. If used, JPDA_TRANSPORT, JPDA_ADDRESS,
#                   and JPDA_SUSPEND are ignored. Thus, all required jpda
#                   options MUST be specified. The default is:
#
#                   -agentlib:jdwp=transport=$JPDA_TRANSPORT,
#                       address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND
#
#   APP_PID    (Optional) Path of the file which should contains the pid
#                   of the catalina startup java process, when start (fork) is
#                   used
#
#   LOGGING_CONFIG  (Optional) Override Tomcat's logging config file
#                   Example (all one line)
#                   LOGGING_CONFIG="-Djava.util.logging.config.file=$APP_BASE/conf/logging.properties"
#
#   LOGGING_MANAGER (Optional) Override Tomcat's logging manager
#                   Example (all one line)
#                   LOGGING_MANAGER="-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager"
# -----------------------------------------------------------------------------

# OS specific support.  $var _must_ be set to either true or false.

# -----------------------------------------------------------------------------
# Control Script for the APP Server
# Compatible with ECS / VM and Docker / K8s Container
# -----------------------------------------------------------------------------

MAIN_CLASS=com.vti.vops.VopsApplication

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
darwin=false
os400=false
case "`uname`" in
CYGWIN*) cygwin=true;;
Darwin*) darwin=true;;
OS400*) os400=true;;
esac

# resolve links - $0 may be a softlink
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=`dirname "$PRG"`
APP_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

case $APP_HOME in
  *:*) echo "Using APP_HOME:   $APP_HOME";
       echo "Unable to start as APP_HOME contains a colon (:) character";
       exit 1;
esac

if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$JRE_HOME" ] && JRE_HOME=`cygpath --unix "$JRE_HOME"`
  [ -n "$APP_HOME" ] && APP_HOME=`cygpath --unix "$APP_HOME"`
fi

if $os400; then
  COMMAND='chgjob job('$JOBNAME') runpty(6)'
  system $COMMAND
  export QIBM_MULTI_THREADED=Y
fi

if $os400; then
  . "$APP_HOME"/bin/setclasspath.sh
else
  if [ -r "$APP_HOME"/bin/setclasspath.sh ]; then
    . "$APP_HOME"/bin/setclasspath.sh
  else
    echo "Cannot find $APP_HOME/bin/setclasspath.sh"
    exit 1
  fi
fi

APP_LOG_DIR=$APP_HOME/logs
[ ! -d "$APP_LOG_DIR" ] && mkdir -p "$APP_LOG_DIR"

GCOPTS="-Dapp.home=$APP_HOME -Xms512M -Xmx4G -XX:+UseG1GC"
CLASSPATH=$APP_HOME/lib/*:$APP_HOME/conf

# -----------------------------------------------------------------------------
# Detect Container Environment
# -----------------------------------------------------------------------------
is_container=false
if grep -qE 'docker|lxc|kubepods' /proc/self/cgroup; then
  is_container=true
fi

echo "Container Mode: $is_container"

# -----------------------------------------------------------------------------
# Start Application
# -----------------------------------------------------------------------------

if $is_container; then
  echo "Running in container, start in foreground..."

  if [ -x "$JAVA_HOME/bin/java" ]; then
    exec "$JAVA_HOME/bin/java" $GCOPTS -cp "$CLASSPATH" $MAIN_CLASS "$@"
  else
    exec "$JRE_HOME/bin/java" $GCOPTS -cp "$CLASSPATH" $MAIN_CLASS "$@"
  fi

else
  echo "Running in ECS/VM mode, start in background..."

  APP_PID=`ps -ef | grep -v grep | grep "$APP_HOME" | awk '{print $2}'`

  if [ -z "$APP_PID" ]; then
    if [ -x "$JAVA_HOME/bin/java" ]; then
      nohup "$JAVA_HOME/bin/java" $GCOPTS -cp "$CLASSPATH" $MAIN_CLASS "$@" \
        > "$APP_LOG_DIR/application.out" 2>&1 &
    else
      nohup "$JRE_HOME/bin/java" $GCOPTS -cp "$CLASSPATH" $MAIN_CLASS "$@" \
        > "$APP_LOG_DIR/application.out" 2>&1 &
    fi

    sleep 2
    APP_PID=`ps -ef | grep -v grep | grep "$APP_HOME" | awk '{print $2}'`
    echo "Application started, PID: $APP_PID"
  else
    echo "Sorry, Application is already running. PID: $APP_PID"
  fi
fi
