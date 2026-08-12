package fundcopilot.observation.vo;

import java.time.LocalDate;
import java.util.List;

public record ObservationCategoryVO(
        String category,
        String categoryName,
        LocalDate rankDate,
        int universeSize,
        List<ObservationFundVO> funds
) {
}
