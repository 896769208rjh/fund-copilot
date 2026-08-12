package fundcopilot.observation.controller;

import fundcopilot.common.ApiResponse;
import fundcopilot.observation.model.FundCategory;
import fundcopilot.observation.service.ObservationBoardService;
import fundcopilot.observation.vo.ObservationBoardVO;
import fundcopilot.observation.vo.ObservationCategoryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observations")
public class ObservationController {
    private final ObservationBoardService boardService;

    public ObservationController(ObservationBoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public ApiResponse<ObservationBoardVO> board() {
        return ApiResponse.ok(boardService.getBoard());
    }

    @GetMapping("/{category}")
    public ApiResponse<ObservationCategoryVO> category(@PathVariable String category) {
        return ApiResponse.ok(boardService.getCategory(FundCategory.fromCode(category)));
    }
}
