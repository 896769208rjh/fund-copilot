package fundcopilot.fund.controller;

import fundcopilot.common.ApiResponse;
import fundcopilot.fund.service.FundQueryService;
import fundcopilot.fund.vo.FundSearchItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alipay")
public class AlipayFundPoolController {
    private final FundQueryService fundQueryService;

    public AlipayFundPoolController(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    @GetMapping("/fund-pool")
    public ApiResponse<List<FundSearchItemVO>> fundPool() {
        return ApiResponse.ok(fundQueryService.listAlipayFundPool());
    }
}
