package vn.ducthe.organic.dto.response;

import java.util.List;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.TargetRule;
import vn.ducthe.organic.entity.PromotionEntity;

public final class PromotionAdminResponses {
    private PromotionAdminResponses() {}

    public record Detail(PromotionEntity promotion, List<TargetRule> targets) {}
}
