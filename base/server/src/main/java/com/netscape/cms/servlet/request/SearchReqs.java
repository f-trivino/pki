// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cms.servlet.request;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dogtagpki.server.authentication.AuthToken;
import org.dogtagpki.server.authorization.AuthzToken;

import com.netscape.certsrv.authorization.EAuthzAccessDenied;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IArgBlock;
import com.netscape.certsrv.base.ISubsystem;
import com.netscape.certsrv.request.IRequestList;
import com.netscape.cms.servlet.base.CMSServlet;
import com.netscape.cms.servlet.common.CMSRequest;
import com.netscape.cms.servlet.common.CMSTemplate;
import com.netscape.cms.servlet.common.CMSTemplateParams;
import com.netscape.cms.servlet.common.ECMSGWException;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;
import com.netscape.cmscore.base.ArgBlock;
import com.netscape.cmscore.base.ConfigStore;
import com.netscape.cmscore.request.Request;
import com.netscape.cmscore.request.RequestRepository;

/**
 * Search for requests matching complex query filter.
 */
public class SearchReqs extends CMSServlet {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SearchReqs.class);

    protected final static String TPL_FILE = "queryReq.template";
    protected final static String PROP_MAX_SEARCH_RETURNS = "maxSearchReqReturns";
    protected final static String PROP_PARSER = "parser";
    protected final static String CURRENT_TIME = "currentTime";
    protected final static String OUT_TOTALCOUNT = "totalRecordCount";
    protected final static String OUT_CURRENTCOUNT = "currentRecordCount";
    protected final static int MAX_RESULTS = 1000;

    protected RequestRepository requestRepository;
    protected IReqParser mParser;
    protected String mFormPath;
    protected int mMaxReturns = MAX_RESULTS;
    protected int mTimeLimits = 30; /* in seconds */

    /**
     * Constructs query key servlet.
     */
    public SearchReqs() {
    }

    /**
     * Initialize the servlet. This servlet uses queryReq.template
     * to render the response.
     *
     * @param sc servlet configuration, read from the web.xml file
     */
    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc);
        // override success to render own template.
        mTemplates.remove(CMSRequest.SUCCESS);

        CMSEngine engine = CMS.getCMSEngine();
        ISubsystem sub = mAuthority;
        ConfigStore authConfig = sub.getConfigStore();

        if (authConfig != null) {
            try {
                mMaxReturns = authConfig.getInteger(PROP_MAX_SEARCH_RETURNS, MAX_RESULTS);
            } catch (EBaseException e) {
                // do nothing
            }
        }

        requestRepository = engine.getRequestRepository();

        mFormPath = "/" + mAuthority.getId() + "/" + TPL_FILE;

        /* Server-Side time limit */
        try {
            int maxResults = Integer.parseInt(sc.getInitParameter("maxResults"));
            if (maxResults < mMaxReturns)
                mMaxReturns = maxResults;
        } catch (Exception e) {
            /* do nothing, just use the default if integer parsing failed */
        }
        try {
            mTimeLimits = Integer.parseInt(sc.getInitParameter("timeLimits"));
        } catch (Exception e) {
            /* do nothing, just use the default if integer parsing failed */
        }

        // override success and error templates to null -
        // handle templates locally.
        mTemplates.remove(CMSRequest.SUCCESS);
        mTemplates.remove(CMSRequest.ERROR);

        if (mOutputTemplatePath != null)
            mFormPath = mOutputTemplatePath;
    }

    /**
     * Serves HTTP request. This format of this request is as follows:
     * queryCert?
     * [maxCount=<number>]
     * [queryFilter=<filter>]
     * [revokeAll=<filter>]
     */
    @Override
    public void process(CMSRequest cmsReq) throws EBaseException {
        HttpServletRequest req = cmsReq.getHttpReq();
        HttpServletResponse resp = cmsReq.getHttpResp();

        AuthToken authToken = authenticate(cmsReq);

        AuthzToken authzToken = null;

        try {
            authzToken = authorize(mAclMethod, authToken,
                        mAuthzResourceName, "list");
        } catch (EAuthzAccessDenied e) {
            logger.warn(CMS.getLogMessage("ADMIN_SRVLT_AUTH_FAILURE", e.toString()), e);

        } catch (Exception e) {
            logger.warn(CMS.getLogMessage("ADMIN_SRVLT_AUTH_FAILURE", e.toString()), e);
        }

        if (authzToken == null) {
            cmsReq.setStatus(CMSRequest.UNAUTHORIZED);
            return;
        }

        EBaseException error = null;
        int maxResults = -1;
        int timeLimit = -1;

        ArgBlock header = new ArgBlock();
        ArgBlock ctx = new ArgBlock();
        CMSTemplateParams argSet = new CMSTemplateParams(header, ctx);

        CMSTemplate form = null;
        Locale[] locale = new Locale[1];

        try {
            form = getTemplate(mFormPath, req, locale);
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERR_GET_TEMPLATE", e.toString()), e);
            throw new ECMSGWException(CMS.getUserMessage("CMS_GW_DISPLAY_TEMPLATE_ERROR"), e);
        }

        try {
            String maxResultsStr = req.getParameter("maxResults");

            if (maxResultsStr != null && maxResultsStr.length() > 0)
                maxResults = Integer.parseInt(maxResultsStr);
            String timeLimitStr = req.getParameter("timeLimit");

            if (timeLimitStr != null && timeLimitStr.length() > 0)
                timeLimit = Integer.parseInt(timeLimitStr);

            process(argSet, header, req.getParameter("queryRequestFilter"), authToken,
                    maxResults, timeLimit, req, resp, locale[0]);

        } catch (NumberFormatException e) {
            logger.warn(CMS.getLogMessage("BASE_INVALID_NUMBER_FORMAT"), e);
            error = new EBaseException(CMS.getUserMessage(getLocale(req), "CMS_BASE_INVALID_NUMBER_FORMAT"), e);

        } catch (EBaseException e) {
            error = e;
        }

        try {
            ServletOutputStream out = resp.getOutputStream();

            if (error == null) {
                String xmlOutput = req.getParameter("xml");
                if (xmlOutput != null && xmlOutput.equals("true")) {
                    outputXML(resp, argSet);
                } else {
                    cmsReq.setStatus(CMSRequest.SUCCESS);
                    resp.setContentType("text/html");
                    form.renderOutput(out, argSet);
                }
            } else {
                cmsReq.setStatus(CMSRequest.ERROR);
                cmsReq.setError(error);
            }

        } catch (IOException e) {
            logger.warn(CMS.getLogMessage("CMSGW_ERR_OUT_STREAM_TEMPLATE", e.toString()), e);
            throw new ECMSGWException(CMS.getUserMessage("CMS_GW_DISPLAY_TEMPLATE_ERROR"), e);
        }
    }

    /**
     * Process the key search.
     */
    private void process(CMSTemplateParams argSet, IArgBlock header,
            String filter, AuthToken token,
            int maxResults, int timeLimit,
            HttpServletRequest req, HttpServletResponse resp,
            Locale locale)
            throws EBaseException {

        try {
            long startTime = new Date().getTime();

            if (filter.indexOf(CURRENT_TIME, 0) > -1) {
                filter = insertCurrentTime(filter);
            }

            String owner = req.getParameter("owner");
            String requestowner_filter = "";
            String newfilter = "";
            if (owner.length() == 0) {
                newfilter = filter;
            } else {
                if (owner.equals("self")) {
                    String self_uid = token.getInString(AuthToken.USER_ID);
                    requestowner_filter = "(requestowner=" + self_uid + ")";
                } else {
                    String uid = req.getParameter("uid");
                    requestowner_filter = "(requestowner=" + uid + ")";
                }
                newfilter = "(&" + requestowner_filter + filter.substring(2);
            }
            // xxx the filter includes serial number range???
            if (maxResults == -1 || maxResults > mMaxReturns) {
                logger.debug("Resetting maximum of returned results from " + maxResults + " to " + mMaxReturns);
                maxResults = mMaxReturns;
            }
            if (timeLimit == -1 || timeLimit > mTimeLimits) {
                logger.debug("Resetting timelimit from " + timeLimit + " to " + mTimeLimits);
                timeLimit = mTimeLimits;
            }
            IRequestList list = (timeLimit > 0) ?
                    requestRepository.listRequestsByFilter(newfilter, maxResults, timeLimit) :
                    requestRepository.listRequestsByFilter(newfilter, maxResults);

            int count = 0;

            while (list != null && list.hasMoreElements()) {
                Request request = list.nextRequestObject();

                if (request != null) {
                    count++;
                    ArgBlock rarg = new ArgBlock();
                    mParser.fillRequestIntoArg(locale, request, argSet, rarg);
                    argSet.addRepeatRecord(rarg);
                    long endTime = new Date().getTime();

                    header.addIntegerValue(OUT_CURRENTCOUNT, count);
                    header.addStringValue("time", Long.toString(endTime - startTime));
                }
            }
            header.addIntegerValue(OUT_TOTALCOUNT, count);
        } catch (EBaseException e) {
            CMS.getLogMessage("CMSGW_ERROR_LISTCERTS", e.toString());
            throw e;
        }
        return;
    }

    private String insertCurrentTime(String filter) {
        Date now = null;
        StringBuffer newFilter = new StringBuffer();
        int k = 0;
        int i = filter.indexOf(CURRENT_TIME, k);

        while (i > -1) {
            if (now == null)
                now = new Date();
            newFilter.append(filter.substring(k, i));
            newFilter.append(now.getTime());
            k = i + CURRENT_TIME.length();
            i = filter.indexOf(CURRENT_TIME, k);
        }
        if (k > 0) {
            newFilter.append(filter.substring(k, filter.length()));
        }
        return newFilter.toString();
    }
}
