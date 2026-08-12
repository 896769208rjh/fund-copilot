package fundcopilot.observation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_master")
public class FundMasterDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String identityKey;
    private String primaryFundCode;
    private String fundName;
    private String fundCategory;
    private String fundCompany;
    private String fundManager;
    private BigDecimal latestScale;
    private LocalDate scaleDate;
    private String sourceUrl;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
