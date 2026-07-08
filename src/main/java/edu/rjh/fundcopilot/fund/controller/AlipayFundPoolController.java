package edu.rjh.fundcopilot.fund.controller;

import edu.rjh.fundcopilot.common.ApiResponse;
import edu.rjh.fundcopilot.fund.service.FundQueryService;
import edu.rjh.fundcopilot.fund.vo.FundSearchItemVO;
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
