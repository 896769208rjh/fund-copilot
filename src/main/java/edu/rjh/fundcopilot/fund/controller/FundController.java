package edu.rjh.fundcopilot.fund.controller;

import edu.rjh.fundcopilot.common.ApiResponse;
import edu.rjh.fundcopilot.fund.service.FundQueryService;
import edu.rjh.fundcopilot.fund.vo.FundAnalysisResultVO;
import edu.rjh.fundcopilot.fund.vo.FundDetailVO;
import edu.rjh.fundcopilot.fund.vo.FundNavPointVO;
import edu.rjh.fundcopilot.fund.vo.FundSearchItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/funds")
public class FundController {
    private final FundQueryService fundQueryService;

    public FundController(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    @GetMapping("/search")
    public ApiResponse<List<FundSearchItemVO>> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(fundQueryService.search(keyword));
    }

    @GetMapping("/{fundCode}")
    public ApiResponse<FundDetailVO> detail(@PathVariable String fundCode) {
        return ApiResponse.ok(fundQueryService.getDetail(fundCode));
    }

    @GetMapping("/{fundCode}/nav")
    public ApiResponse<List<FundNavPointVO>> nav(@PathVariable String fundCode,
                                                 @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(fundQueryService.getNavPoints(fundCode, limit));
    }

    @GetMapping("/{fundCode}/analysis")
    public ApiResponse<FundAnalysisResultVO> analysis(@PathVariable String fundCode) {
        return ApiResponse.ok(fundQueryService.analyze(fundCode));
    }

    @PostMapping("/{fundCode}/sync")
    public ApiResponse<FundDetailVO> sync(@PathVariable String fundCode) {
        return ApiResponse.ok(fundQueryService.syncFund(fundCode));
    }
}
