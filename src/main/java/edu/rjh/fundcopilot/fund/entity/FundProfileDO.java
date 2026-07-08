package edu.rjh.fundcopilot.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("fund_profile")
public class FundProfileDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private String fundName;
    private String fundType;
    private String fundCompany;
    private String fundManager;
    private String riskLevel;
    private String purchaseStatus;
    private String redeemStatus;
    private BigDecimal latestNav;
    private LocalDate latestNavDate;
    private String sourceUrl;
    private Boolean stale;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    public String getFundCompany() { return fundCompany; }
    public void setFundCompany(String fundCompany) { this.fundCompany = fundCompany; }
    public String getFundManager() { return fundManager; }
    public void setFundManager(String fundManager) { this.fundManager = fundManager; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getPurchaseStatus() { return purchaseStatus; }
    public void setPurchaseStatus(String purchaseStatus) { this.purchaseStatus = purchaseStatus; }
    public String getRedeemStatus() { return redeemStatus; }
    public void setRedeemStatus(String redeemStatus) { this.redeemStatus = redeemStatus; }
    public BigDecimal getLatestNav() { return latestNav; }
    public void setLatestNav(BigDecimal latestNav) { this.latestNav = latestNav; }
    public LocalDate getLatestNavDate() { return latestNavDate; }
    public void setLatestNavDate(LocalDate latestNavDate) { this.latestNavDate = latestNavDate; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public Boolean getStale() { return stale; }
    public void setStale(Boolean stale) { this.stale = stale; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
