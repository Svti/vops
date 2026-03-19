package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vti.vops.entity.SshKey;
import com.vti.vops.mapper.SshKeyMapper;
import com.vti.vops.service.ISshKeyService;
import com.vti.vops.util.EncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SshKeyServiceImpl extends ServiceImpl<SshKeyMapper, SshKey> implements ISshKeyService {

    private final EncryptUtil encryptUtil;

    @Override
    public List<SshKey> listNames() {
        return list(new LambdaQueryWrapper<SshKey>()
                .select(SshKey::getId, SshKey::getName, SshKey::getDescription)
                .orderByAsc(SshKey::getId));
    }

    @Override
    public String getDecryptedContent(Long id) {
        if (id == null) return null;
        SshKey key = getById(id);
        if (key == null || key.getContent() == null || key.getContent().isEmpty()) return null;
        String dec = encryptUtil.decrypt(key.getContent());
        if (dec == null || dec.startsWith(EncryptUtil.PREFIX)) {
            if (dec != null) log.warn("SshKey id={} decrypt returned still-encrypted", id);
            return null;
        }
        return dec;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(SshKey entity) {
        if (entity.getId() != null) {
            SshKey existing = getById(entity.getId());
            if (existing != null && (entity.getContent() == null || entity.getContent().isEmpty())) {
                entity.setContent(existing.getContent());
            }
        }
        if (entity.getContent() != null && !entity.getContent().isEmpty() && !encryptUtil.isEncrypted(entity.getContent())) {
            entity.setContent(encryptUtil.encrypt(entity.getContent()));
        }
        return entity.getId() == null ? super.save(entity) : updateById(entity);
    }
}
