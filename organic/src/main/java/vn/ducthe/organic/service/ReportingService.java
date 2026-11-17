package vn.ducthe.organic.service;

import java.time.LocalDate;
import vn.ducthe.organic.dto.response.ReportingResponses.Sales;
import vn.ducthe.organic.dto.response.ReportingResponses.TopSkus;

public interface ReportingService {

    Sales sales(LocalDate from, LocalDate to);

    TopSkus topSkus(LocalDate from, LocalDate to, int limit);
}
