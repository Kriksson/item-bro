package ItemBro.Advice.DTO;

import java.util.List;

public record AdvicePageDTO(List<AdviceDTO> content, int page, int size, long totalElements, long totalPages) {
}
