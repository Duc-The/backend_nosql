package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.ReceivingPayloads.SupplierInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.SupplierStatus;
import vn.ducthe.organic.entity.SupplierEntity;

public interface SupplierService {

    PageData<SupplierEntity> list(String text, SupplierStatus status, int page, int size);

    SupplierEntity get(String id);

    SupplierEntity active(String id);

    SupplierEntity create(SupplierInput input);

    SupplierEntity edit(String id, SupplierInput input);

    SupplierEntity status(String id, SupplierStatus status);
}
