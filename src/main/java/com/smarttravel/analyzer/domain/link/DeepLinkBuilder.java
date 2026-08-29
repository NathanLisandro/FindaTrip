package com.smarttravel.analyzer.domain.link;

import com.smarttravel.analyzer.domain.model.shared.SearchCriteria;
import java.net.URI;

public interface DeepLinkBuilder {
    String partnerName();
    URI searchUrl(SearchCriteria criteria);
}
