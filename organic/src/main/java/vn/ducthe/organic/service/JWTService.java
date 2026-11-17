package vn.ducthe.organic.service;

import vn.ducthe.organic.dto.response.JWTResponses.Tokens;
import vn.ducthe.organic.entity.AccountEntity;

public interface JWTService {
    Tokens issue(AccountEntity account);

    Tokens refresh(String refreshToken);
}
