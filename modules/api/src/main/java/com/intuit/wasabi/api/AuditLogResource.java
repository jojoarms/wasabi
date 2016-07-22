/*******************************************************************************
 * Copyright 2016 Intuit
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.intuit.wasabi.api;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.intuit.wasabi.api.pagination.PaginationHelper;
import com.intuit.wasabi.auditlog.AuditLog;
import com.intuit.wasabi.auditlogobjects.AuditLogEntry;
import com.intuit.wasabi.authorization.Authorization;
import com.intuit.wasabi.authorizationobjects.Permission;
import com.intuit.wasabi.experimentobjects.Application;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intuit.wasabi.api.APISwaggerResource.*;
import static com.intuit.wasabi.authorizationobjects.Permission.ADMIN;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * The API endpoint /logs provides audit logs for application admins.
 *
 * The logs can be filtered, sorted, paginated. By default the page {@link APISwaggerResource#DEFAULT_PAGE} is returned,
 * containing the last {@link APISwaggerResource#DEFAULT_PER_PAGE} actions. The logs are by default sorted by their
 * occurence date, descending.
 */
@Path("/v1/logs")
@Produces(APPLICATION_JSON)
@Singleton
@Api(value = "Audit Logs (Activity Logs about changes in experiments-buckets)", produces = "application/json")
public class AuditLogResource {

    private final AuditLog auditLog;
    private final Authorization authorization;
    private final HttpHeader httpHeader;
    private final PaginationHelper<AuditLogEntry> paginationHelper;

    /**
     * Instantiates a LogsResource.
     *
     * @param auditLog the auditlog repository
     * @param authorization the authorization
     * @param httpHeader the HTTP header
     * @param paginationHelper the pagination helper
     */
    @Inject
    AuditLogResource(final AuditLog auditLog, final Authorization authorization,
                 final HttpHeader httpHeader, final PaginationHelper<AuditLogEntry> paginationHelper) {
        this.auditLog = auditLog;
        this.authorization = authorization;
        this.httpHeader = httpHeader;
        this.paginationHelper = paginationHelper;
    }

    /**
     * Returns a list of audit log entries for the specified application if the requesting user has access to it.
     * To have access the user needs {@link Permission#ADMIN} permissions for the application.
     *
     * Before returning the paginated list of log entries it gets
     * <ul>
     * <li>filtered,</li>
     * <li>sorted,</li>
     * <li>counted,</li>
     * <li>paginated.</li>
     * </ul>
     *
     * @param authorizationHeader the authentication headers
     * @param applicationName the name of the application for which the log should be fetched
     * @param page the page which should be returned, defaults to 1 (latest changes)
     * @param perPage the number of log entries per page, defaults to 10
     * @param sort the sorting rules
     * @param filter the filter rules
     * @param timezoneOffset the timezone offset from UTC
     * @return a response containing a list with {@code 0 - perPage} experiments, if that many are on the page.
     */
    @GET
    @Path("/applications/{applicationName}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Returns all logs for the specified application if the requesting user has Admin permissions.",
            notes = "Returns all logs for the specified application if the requesting user has Admin permissions. "
                    + "The parameters allow for filtering, sorting, and pagination.",
            response = Response.class,
            httpMethod = "GET",
            produces = "application/json",
            protocols = "https")
    @Timed(name = "getLogs")
    public Response getLogs(@HeaderParam(AUTHORIZATION)
                            @ApiParam(value = EXAMPLE_AUTHORIZATION_HEADER, required = true)
                            final String authorizationHeader,

                            @PathParam("applicationName")
                            @ApiParam(value = "Application Name")
                            final Application.Name applicationName,

                            @QueryParam("page")
                            @DefaultValue(DEFAULT_PAGE)
                            @ApiParam(name = "page", value = "Defines the page to retrieve", defaultValue = DEFAULT_PAGE)
                            final int page,

                            @QueryParam("per_page")
                            @DefaultValue(DEFAULT_PER_PAGE)
                            @ApiParam(name = "per_page", value = "Defines the entries per page.", defaultValue = DEFAULT_PER_PAGE)
                            final int perPage,

                            @QueryParam("sort")
                            @DefaultValue("")
                            @ApiParam(name = "sort", defaultValue = "", value = "")
                            final String sort,

                            @QueryParam("filter")
                            @DefaultValue("")
                            @ApiParam(name = "filter", defaultValue = "", value = "")
                            final String filter,

                            @QueryParam("timezone")
                            @DefaultValue("+0000")
                            @ApiParam(name = "timezone", defaultValue = "+0000", value = EXAMPLE_TIMEZONE)
                            final String timezoneOffset) {
        List<AuditLogEntry> auditLogs;

        if (applicationName != null) {
            authorization.checkUserPermissions(authorization.getUser(authorizationHeader), applicationName, ADMIN);
            auditLogs = auditLog.getAuditLogs(applicationName);
        } else {
            authorization.checkSuperAdmin(authorization.getUser(authorizationHeader));
            auditLogs = auditLog.getAuditLogs();
        }

        Map<String, Object> auditLogResponse = new HashMap<>();
        auditLogResponse.put("totalEntries", auditLogs.size());
        auditLogResponse.put("logEntries", paginationHelper.paginate(auditLogs, filter, timezoneOffset, sort, page, perPage));

        return httpHeader.headers().entity(auditLogResponse).build();
    }

    /**
     * Returns a list of audit log entries for all applications, if the requesting user has access to it.
     * To have access the user needs {@link Permission#SUPERADMIN} permissions.
     *
     * Before returning the paginated list of log entries it gets
     * <ul>
     * <li>filtered,</li>
     * <li>sorted,</li>
     * <li>paginated.</li>
     * </ul>
     *
     * @param authorizationHeader the authentication headers
     * @param page the page which should be returned, defaults to 1 (latest changes)
     * @param perPage the number of log entries per page, defaults to 10
     * @param sort the sorting rules
     * @param filter the filter rules
     * @param timezoneOffset the time zone offset from UTC
     * @return a response containing a list with {@code 0} to {@code perPage} experiments, if that many are on the page.
     */
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Returns all logs if the requesting user has SuperAdmin permissions.",
            notes = "Returns all logs if the requesting user has SuperAdmin permissions. "
                    + "The parameters allow for filtering, sorting, and pagination.",
            response = Response.class,
            httpMethod = "GET",
            produces = "application/json",
            protocols = "https")
    @Timed(name = "getCompleteLogs")
    public Response getCompleteLogs(@HeaderParam(AUTHORIZATION)
                                    @ApiParam(value = EXAMPLE_AUTHORIZATION_HEADER, required = true)
                                    final String authorizationHeader,

                                    @QueryParam("page")
                                    @DefaultValue(DEFAULT_PAGE)
                                    @ApiParam(name = "page", defaultValue = DEFAULT_PAGE, value = EXAMPLE_PAGE)
                                    final int page,

                                    @QueryParam("per_page")
                                    @DefaultValue(DEFAULT_PER_PAGE)
                                    @ApiParam(name = "per_page", defaultValue = DEFAULT_PER_PAGE, value = EXAMPLE_PER_PAGE)
                                    final int perPage,

                                    @QueryParam("sort")
                                    @DefaultValue("")
                                    @ApiParam(name = "sort", defaultValue = "", value = EXAMPLE_SORT)
                                    final String sort,

                                    @QueryParam("filter")
                                    @DefaultValue("")
                                    @ApiParam(name = "filter", defaultValue = "", value = EXAMPLE_FILTER)
                                    final String filter,

                                    @QueryParam("timezone")
                                    @DefaultValue("+0000")
                                    @ApiParam(name = "timezone", defaultValue = "+0000", value = EXAMPLE_TIMEZONE)
                                    final String timezoneOffset) {
        return getLogs(authorizationHeader, null, page, perPage, sort, filter, timezoneOffset);
    }

}
