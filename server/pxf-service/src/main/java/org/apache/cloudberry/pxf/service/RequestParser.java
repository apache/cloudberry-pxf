package org.apache.cloudberry.pxf.service;

import org.apache.cloudberry.pxf.api.model.RequestContext;

/**
 * Parser for incoming requests responsible for extracting request parameters.
 *
 * @param <T> type of request
 */
public interface RequestParser<T> {

    /**
     * Parses the request and constructs RequestContext instance
     * @param request request data
     * @param requestType type of request: read/write/fragmenter, etc.
     * @return parsed information as an instance of RequestContext
     */
    RequestContext parseRequest(T request, RequestContext.RequestType requestType);
}
