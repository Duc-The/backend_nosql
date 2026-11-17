package vn.ducthe.organic.service;

import vn.ducthe.organic.dto.request.AuthPayloads;
import vn.ducthe.organic.dto.response.AuthResponses;
import vn.ducthe.organic.dto.response.JWTResponses;

public interface AuthService {

    void register(AuthPayloads.Register request);

    void resend(String address);

    void verifyRegistration(AuthPayloads.OtpRequest request);

    JWTResponses.Tokens login(AuthPayloads.Login request);

    JWTResponses.Tokens googleLogin(String idToken);

    void forgot(String address);

    AuthResponses.ResetGrant verifyReset(AuthPayloads.OtpRequest request);

    void reset(AuthPayloads.Reset request);

    void changePassword(String id, AuthPayloads.ChangePassword request);
}
