package com.amz.spyglass.service;

import com.amz.spyglass.dto.AsinKeywordDto;
import com.amz.spyglass.model.AsinKeywords;
import com.amz.spyglass.repository.AsinKeywordsRepository;
import com.amz.spyglass.repository.AsinRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.amz.spyglass.model.AsinModel;

/**
 * V2.1 F-BIZ-001: 管理 ASIN 关键词的服务。
 * <p>
 * 负责处理 ASIN 关键词的增删改查逻辑。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@Slf4j
@Service
public class AsinKeywordsService {

    private final AsinKeywordsRepository asinKeywordsRepository;
    private final AsinRepository asinRepository;

    @Autowired
    public AsinKeywordsService(AsinKeywordsRepository asinKeywordsRepository, AsinRepository asinRepository) {
        this.asinKeywordsRepository = asinKeywordsRepository;
        this.asinRepository = asinRepository;
    }

    /**
     * 为指定 ASIN 添加一个新关键词。
     *
     * @param asin       亚马逊标准识别码
     * @param keywordDto 包含关键词信息的 DTO
     * @return 已保存的关键词 DTO
     */
    @Transactional
    public AsinKeywordDto addKeyword(String asin, AsinKeywordDto keywordDto) {
        AsinModel asinModel = asinRepository.findByAsin(asin)
            .orElseThrow(() -> new EntityNotFoundException("ASIN not found: " + asin));

        AsinKeywords newKeyword = new AsinKeywords();
        newKeyword.setAsin(asinModel);
        newKeyword.setKeyword(keywordDto.getKeyword());
        newKeyword.setIsTracked(keywordDto.getIsTracked() != null ? keywordDto.getIsTracked() : Boolean.TRUE);

        AsinKeywords savedKeyword = asinKeywordsRepository.save(newKeyword);
        log.info("成功为 ASIN: {} 添加关键词: '{}'", asin, savedKeyword.getKeyword());
        return convertToDto(savedKeyword);
    }

    /**
     * 获取指定 ASIN 的所有关键词。
     *
     * @param asin 亚马逊标准识别码
     * @return 关键词 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<AsinKeywordDto> getKeywordsByAsin(String asin) {
        // 验证 ASIN 是否存在，便于在 OpenAPI 中准确返回 404 状态
        AsinModel asinModel = asinRepository.findByAsin(asin)
            .orElseThrow(() -> new EntityNotFoundException("ASIN not found: " + asin));

        return asinKeywordsRepository.findByAsinId(asinModel.getId()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 更新一个关键词的信息（例如，是否追踪）。
     *
     * @param keywordId  关键词记录的唯一ID
     * @param keywordDto 包含更新信息的 DTO
     * @return 更新后的关键词 DTO
     */
    @Transactional
    public AsinKeywordDto updateKeyword(Long keywordId, AsinKeywordDto keywordDto) {
        Objects.requireNonNull(keywordId, "keywordId must not be null");

        AsinKeywords keyword = asinKeywordsRepository.findById(keywordId)
                .orElseThrow(() -> new EntityNotFoundException("Keyword not found with id: " + keywordId));

        keyword.setKeyword(keywordDto.getKeyword());
        keyword.setIsTracked(keywordDto.getIsTracked() != null ? keywordDto.getIsTracked() : Boolean.TRUE);

        AsinKeywords updatedKeyword = asinKeywordsRepository.save(keyword);
        log.info("成功更新关键词 ID: {}", keywordId);
        return convertToDto(updatedKeyword);
    }

    /**
     * 删除一个关键词。
     *
     * @param keywordId 关键词记录的唯一ID
     */
    @Transactional
    public void deleteKeyword(Long keywordId) {
        Objects.requireNonNull(keywordId, "keywordId must not be null");

        if (!asinKeywordsRepository.existsById(keywordId)) {
            throw new EntityNotFoundException("Keyword not found with id: " + keywordId);
        }
        asinKeywordsRepository.deleteById(keywordId);
        log.info("成功删除关键词 ID: {}", keywordId);
    }

    private AsinKeywordDto convertToDto(AsinKeywords keyword) {
        AsinKeywordDto dto = new AsinKeywordDto();
        dto.setId(keyword.getId());
        dto.setAsin(keyword.getAsin().getAsin());
        dto.setKeyword(keyword.getKeyword());
        dto.setIsTracked(keyword.getIsTracked());
        return dto;
    }
}
