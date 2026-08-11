package com.Jolie.career_toolkit.offer;

import com.Jolie.career_toolkit.offer.dto.DecideOfferRequest;
import com.Jolie.career_toolkit.offer.dto.OfferResponse;
import com.Jolie.career_toolkit.offer.dto.SaveOfferRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    /** 我手上所有的 offer，依回覆死線排序。 */
    @GetMapping("/offers")
    public List<OfferResponse> listMine() {
        return offerService.listMine().stream().map(OfferResponse::from).toList();
    }

    @GetMapping("/applications/{applicationId}/offer")
    public OfferResponse getByApplication(@PathVariable UUID applicationId) {
        return OfferResponse.from(offerService.getByApplication(applicationId));
    }

    /** PUT：一筆投遞只會有一個 offer，重複送出結果相同。 */
    @PutMapping("/applications/{applicationId}/offer")
    public OfferResponse save(@PathVariable UUID applicationId,
                              @Valid @RequestBody SaveOfferRequest request) {
        return OfferResponse.from(offerService.save(applicationId, request));
    }

    @PostMapping("/offers/{id}/decision")
    public OfferResponse decide(@PathVariable UUID id,
                                @Valid @RequestBody DecideOfferRequest request) {
        return OfferResponse.from(offerService.decide(id, request));
    }
}
