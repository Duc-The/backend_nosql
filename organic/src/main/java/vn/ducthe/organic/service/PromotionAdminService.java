package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.Input;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.Status;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.VoucherInput;
import vn.ducthe.organic.dto.response.PromotionAdminResponses.Detail;
import vn.ducthe.organic.entity.CustomerVoucherEntity;
import vn.ducthe.organic.entity.PromotionEntity;
import vn.ducthe.organic.entity.PromotionEntity.Target;

public interface PromotionAdminService {

    PageData<PromotionEntity> list(String q, Target target, Status status, int page, int size);

    Detail detail(String id);

    Detail create(Input input);

    Detail edit(String id, Input input);

    Detail status(String id, Status status);

    CustomerVoucherEntity issueVoucher(String id, VoucherInput input);

    PageData<CustomerVoucherEntity> vouchers(String id, int page, int size);
}
