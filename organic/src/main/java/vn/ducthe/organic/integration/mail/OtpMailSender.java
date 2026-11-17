package vn.ducthe.organic.integration.mail;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class OtpMailSender {
    private static final Logger log = LoggerFactory.getLogger(OtpMailSender.class);
    private final SendGrid sendGrid;
    private final String from;

    public OtpMailSender(@Value("${app.sendgrid.api-key}") String apiKey,
                         @Value("${app.sendgrid.from-email}") String from) {
        this.sendGrid = new SendGrid(apiKey);
        this.from = from;
    }

    public void send(String recipient, String subject, String otp) {
        Mail mail = new Mail(new Email(from), subject, new Email(recipient),
                new Content("text/plain", "Mã xác minh của bạn là " + otp + ". Mã có hiệu lực trong 5 phút."));
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        try {
            request.setBody(mail.build());
            int status = sendGrid.api(request).getStatusCode();
            if (status >= 200 && status < 300) {
                return;
            }
            log.warn("SendGrid rejected OTP delivery, HTTP {}", status);
        } catch (IOException exception) {
            log.warn("SendGrid OTP delivery failed");
        }
        throw new ApiException(ApiError.EMAIL_UNAVAILABLE);
    }
}
