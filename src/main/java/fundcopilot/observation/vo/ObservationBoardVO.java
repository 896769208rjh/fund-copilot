package fundcopilot.observation.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ObservationBoardVO(
        List<ObservationCategoryVO> categories,
        String methodology,
        String disclaimer,
        LocalDateTime generatedAt
) {
}
