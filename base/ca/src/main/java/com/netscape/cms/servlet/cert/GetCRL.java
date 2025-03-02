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
package com.netscape.cms.servlet.cert;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CRLException;
import java.util.Locale;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dogtagpki.server.authentication.AuthToken;
import org.dogtagpki.server.authorization.AuthzToken;
import org.dogtagpki.server.ca.CAEngine;
import org.mozilla.jss.netscape.security.util.Utils;
import org.mozilla.jss.netscape.security.x509.X509CRLImpl;

import com.netscape.ca.CRLIssuingPoint;
import com.netscape.ca.CertificateAuthority;
import com.netscape.certsrv.authorization.EAuthzAccessDenied;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IArgBlock;
import com.netscape.certsrv.base.ICRLPrettyPrint;
import com.netscape.cms.servlet.base.CMSServlet;
import com.netscape.cms.servlet.common.CMSRequest;
import com.netscape.cms.servlet.common.CMSTemplate;
import com.netscape.cms.servlet.common.CMSTemplateParams;
import com.netscape.cms.servlet.common.ECMSGWException;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ArgBlock;
import com.netscape.cmscore.cert.CrlCachePrettyPrint;
import com.netscape.cmscore.cert.CrlPrettyPrint;
import com.netscape.cmscore.dbs.CRLIssuingPointRecord;
import com.netscape.cmscore.dbs.CRLRepository;

/**
 * Retrieve CRL for a Certificate Authority
 *
 * @version $Revision$, $Date$
 */
public class GetCRL extends CMSServlet {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GetCRL.class);
    private static final long serialVersionUID = 7132206924070383013L;
    private final static String TPL_FILE = "displayCRL.template";
    private String mFormPath = null;

    public GetCRL() {
        super();
    }

    /**
     * initialize the servlet.
     *
     * @param sc servlet configuration, read from the web.xml file
     */
    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc);

        mTemplates.remove(CMSRequest.SUCCESS);
        mFormPath = "/" + mAuthority.getId() + "/" + TPL_FILE;
        if (mOutputTemplatePath != null)
            mFormPath = mOutputTemplatePath;
    }

    /**
     * Process the HTTP request.
     *
     * @param cmsReq the object holding the request and response information
     * @see DisplayCRL#process
     */
    @Override
    protected void process(CMSRequest cmsReq)
            throws EBaseException {
        HttpServletRequest httpReq = cmsReq.getHttpReq();
        HttpServletResponse httpResp = cmsReq.getHttpResp();

        AuthToken authToken = authenticate(cmsReq);

        AuthzToken authzToken = null;

        try {
            authzToken = authorize(mAclMethod, authToken,
                        mAuthzResourceName, "read");
        } catch (EAuthzAccessDenied e) {
            logger.warn(CMS.getLogMessage("ADMIN_SRVLT_AUTH_FAILURE", e.toString()), e);
        } catch (Exception e) {
            logger.warn(CMS.getLogMessage("ADMIN_SRVLT_AUTH_FAILURE", e.toString()), e);
        }

        if (authzToken == null) {
            cmsReq.setStatus(CMSRequest.UNAUTHORIZED);
            return;
        }

        // Construct an ArgBlock
        IArgBlock args = cmsReq.getHttpParams();

        if (!(mAuthority instanceof CertificateAuthority)) {
            logger.error(CMS.getLogMessage("CMSGW_CA_FROM_RA_NOT_IMP"));
            cmsReq.setError(new ECMSGWException(
                    CMS.getUserMessage("CMS_GW_NOT_YET_IMPLEMENTED")));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }

        CMSTemplate form = null;
        Locale[] locale = new Locale[1];

        logger.debug("**** mFormPath before getTemplate = " + mFormPath);
        try {
            form = getTemplate(mFormPath, httpReq, locale);
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERR_GET_TEMPLATE", mFormPath, e.toString()), e);
            cmsReq.setError(new ECMSGWException(
                    CMS.getUserMessage("CMS_GW_DISPLAY_TEMPLATE_ERROR")));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }

        ArgBlock header = new ArgBlock();
        ArgBlock fixed = new ArgBlock();
        CMSTemplateParams argSet = new CMSTemplateParams(header, fixed);

        // Get the operation code
        String op = null;
        String crlId = null;

        op = args.getValueAsString("op", null);
        crlId = args.getValueAsString("crlIssuingPoint", null);
        if (op == null) {
            logger.error(CMS.getLogMessage("CMSGW_NO_OPTIONS_SELECTED"));
            cmsReq.setError(new ECMSGWException(
                    CMS.getUserMessage("CMS_GW_NO_OPTIONS_SELECTED")));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }
        if (crlId == null) {
            logger.error(CMS.getLogMessage("CMSGW_NO_CRL_ISSUING_POINT"));
            cmsReq.setError(new ECMSGWException(
                    CMS.getUserMessage("CMS_GW_NO_CRL_SELECTED")));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }

        CAEngine engine = CAEngine.getInstance();
        CRLRepository crlRepository = engine.getCRLRepository();

        CRLIssuingPointRecord crlRecord = null;
        CertificateAuthority ca = (CertificateAuthority) mAuthority;
        CRLIssuingPoint crlIP = null;
        if (ca != null)
            crlIP = engine.getCRLIssuingPoint(crlId);

        try {
            crlRecord = crlRepository.readCRLIssuingPointRecord(crlId);
        } catch (EBaseException e) {
            logger.error(CMS.getLogMessage("CMSGW_NO_CRL_ISSUING_POINT_FOUND", crlId), e);
            cmsReq.setError(new ECMSGWException(
                    CMS.getUserMessage("CMS_GW_CRL_NOT_FOUND")));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }
        if (crlRecord == null) {
            logger.error(CMS.getLogMessage("CMSGW_CRL_NOT_YET_UPDATED_1", crlId));
            cmsReq.setError(new ECMSGWException(
                    CMS.getUserMessage("CMS_GW_CRL_NOT_UPDATED")));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }

        header.addStringValue("crlIssuingPoint", crlId);
        header.addStringValue("crlNumber", crlRecord.getCRLNumber().toString());
        long lCRLSize = crlRecord.getCRLSize().longValue();

        header.addLongValue("crlSize", lCRLSize);
        if (crlIP != null) {
            header.addStringValue("crlDescription", crlIP.getDescription());
        }

        String crlDisplayType = args.getValueAsString("crlDisplayType", null);
        if (crlDisplayType != null) {
            header.addStringValue("crlDisplayType", crlDisplayType);
        }

        if ((op.equals("checkCRLcache") ||
                (op.equals("displayCRL") && crlDisplayType != null && crlDisplayType.equals("cachedCRL"))) &&
                (crlIP == null || (!crlIP.isCRLCacheEnabled()) || crlIP.isCRLCacheEmpty())) {
            cmsReq.setError(
                    CMS.getUserMessage(
                            ((crlIP != null && crlIP.isCRLCacheEnabled() && crlIP.isCRLCacheEmpty()) ?
                                    "CMS_GW_CRL_CACHE_IS_EMPTY" : "CMS_GW_CRL_CACHE_IS_NOT_ENABLED"), crlId));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }

        byte[] crlbytes = null;

        if (op.equals("importDeltaCRL") || op.equals("getDeltaCRL") ||
                (op.equals("displayCRL") && crlDisplayType != null &&
                crlDisplayType.equals("deltaCRL"))) {
            crlbytes = crlRecord.getDeltaCRL();
        } else if (op.equals("importCRL") || op.equals("getCRL") ||
                   op.equals("checkCRL") ||
                   (op.equals("displayCRL") &&
                           crlDisplayType != null &&
                    (crlDisplayType.equals("entireCRL") ||
                            crlDisplayType.equals("crlHeader") ||
                     crlDisplayType.equals("base64Encoded")))) {
            crlbytes = crlRecord.getCRL();
        }

        if (crlbytes == null && (!op.equals("checkCRLcache")) &&
                (!(op.equals("displayCRL") && crlDisplayType != null &&
                crlDisplayType.equals("cachedCRL")))) {
            logger.error(CMS.getLogMessage("CMSGW_CRL_NOT_YET_UPDATED_1", crlId));
            cmsReq.setError(new ECMSGWException(
                    CMS.getUserMessage("CMS_GW_CRL_NOT_UPDATED")));
            cmsReq.setStatus(CMSRequest.ERROR);
            return;
        }
        byte[] bytes = crlbytes;

        X509CRLImpl crl = null;

        if (op.equals("checkCRL") || op.equals("importCRL") ||
                op.equals("importDeltaCRL") ||
                (op.equals("displayCRL") && crlDisplayType != null &&
                (crlDisplayType.equals("entireCRL") ||
                        crlDisplayType.equals("crlHeader") ||
                        crlDisplayType.equals("base64Encoded") ||
                crlDisplayType.equals("deltaCRL")))) {
            try {
                if (op.equals("displayCRL") && crlDisplayType != null &&
                        crlDisplayType.equals("crlHeader")) {
                    crl = new X509CRLImpl(crlbytes, false);
                } else {
                    crl = new X509CRLImpl(crlbytes);
                }
            } catch (Exception e) {
                logger.error(CMS.getLogMessage("CMSGW_FAILED_DECODE_CRL_1", e.toString()), e);
                cmsReq.setError(new ECMSGWException(
                        CMS.getUserMessage("CMS_GW_DECODE_CRL_FAILED")));
                cmsReq.setStatus(CMSRequest.ERROR);
                return;
            }
            if ((op.equals("importDeltaCRL") || (op.equals("displayCRL") &&
                    crlDisplayType != null && crlDisplayType.equals("deltaCRL"))) &&
                    ((!(crlIP != null && crlIP.isThisCurrentDeltaCRL(crl))) &&
                    (crlRecord.getCRLNumber() == null ||
                            crlRecord.getDeltaCRLNumber() == null ||
                            crlRecord.getDeltaCRLNumber().compareTo(crlRecord.getCRLNumber()) < 0 ||
                            crlRecord.getDeltaCRLSize() == null ||
                    crlRecord.getDeltaCRLSize().longValue() == -1))) {
                logger.error(CMS.getLogMessage("CMSGW_ERR_NO_DELTA_CRL_1"));
                cmsReq.setError(new ECMSGWException(
                        CMS.getUserMessage("CMS_GW_CRL_NOT_UPDATED")));
                cmsReq.setStatus(CMSRequest.ERROR);
                return;
            }
        }

        String mimeType = "application/x-pkcs7-crl";

        if (op.equals("checkCRLcache") || op.equals("checkCRL") || op.equals("displayCRL")) {
            header.addStringValue("toDo", op);
            String certSerialNumber = args.getValueAsString("certSerialNumber", "");

            header.addStringValue("certSerialNumber", certSerialNumber);
            if (certSerialNumber.startsWith("0x")) {
                certSerialNumber = hexToDecimal(certSerialNumber);
            }

            if (op.equals("checkCRLcache")) {
                if (crlIP.getRevocationDateFromCache(
                        new BigInteger(certSerialNumber), false, false) != null) {
                    header.addBooleanValue("isOnCRL", true);
                } else {
                    header.addBooleanValue("isOnCRL", false);
                }
            }

            if (op.equals("checkCRL")) {
                header.addBooleanValue("isOnCRL",
                        crl.isRevoked(new BigInteger(certSerialNumber)));
            }

            if (op.equals("displayCRL")) {
                if (crlDisplayType.equals("entireCRL") || crlDisplayType.equals("cachedCRL")) {
                    ICRLPrettyPrint crlDetails = (crlDisplayType.equals("entireCRL")) ?
                            new CrlPrettyPrint(crl) : new CrlCachePrettyPrint(crlIP);
                    String pageStart = args.getValueAsString("pageStart", null);
                    String pageSize = args.getValueAsString("pageSize", null);

                    if (pageStart != null && pageSize != null) {
                        long lPageStart = 0L;
                        long lPageSize = 0L;
                        try {
                            lPageStart = Long.valueOf(pageStart).longValue();
                        } catch (NumberFormatException e) {
                        }
                        try {
                            lPageSize = Long.valueOf(pageSize).longValue();
                        } catch (NumberFormatException e) {
                        }

                        if (lPageStart < 1)
                            lPageStart = 1;
                        if (lPageSize < 1)
                            lPageSize = 10;

                        header.addStringValue("crlPrettyPrint",
                                 crlDetails.toString(locale[0],
                                         lCRLSize, lPageStart, lPageSize));
                        header.addLongValue("pageStart", lPageStart);
                        header.addLongValue("pageSize", lPageSize);
                    } else {
                        header.addStringValue(
                                "crlPrettyPrint", crlDetails.toString(locale[0]));
                    }
                } else if (crlDisplayType.equals("crlHeader")) {
                    CrlPrettyPrint crlDetails = new CrlPrettyPrint(crl);

                    header.addStringValue(
                            "crlPrettyPrint", crlDetails.toString(locale[0], lCRLSize, 0, 0));
                } else if (crlDisplayType.equals("base64Encoded")) {
                    try {
                        byte[] ba = crl.getEncoded();
                        String crlBase64Encoded = Utils.base64encode(ba, true);
                        int length = crlBase64Encoded.length();
                        int i = 0;
                        int j = 0;
                        int n = 1;

                        while (i < length) {
                            int k = crlBase64Encoded.indexOf('\n', i);
                            if (k < 0)
                                break;

                            if (n < 100) {
                                n++;
                                i = k + 1;
                            } else {
                                n = 1;
                                ArgBlock rarg = new ArgBlock();
                                rarg.addStringValue("crlBase64Encoded", crlBase64Encoded.substring(j, k));
                                i = k + 1;
                                j = i;
                                argSet.addRepeatRecord(rarg);
                            }
                        }
                        if (j < length) {
                            ArgBlock rarg = new ArgBlock();
                            rarg.addStringValue("crlBase64Encoded", crlBase64Encoded.substring(j, length));
                            argSet.addRepeatRecord(rarg);
                        }
                    } catch (CRLException e) {
                    }
                } else if (crlDisplayType.equals("deltaCRL")) {
                    header.addIntegerValue("deltaCRLSize",
                            crl.getNumberOfRevokedCertificates());

                    CrlPrettyPrint crlDetails = new CrlPrettyPrint(crl);

                    header.addStringValue(
                            "crlPrettyPrint", crlDetails.toString(locale[0], 0, 0, 0));

                    try {
                        byte[] ba = crl.getEncoded();
                        String crlBase64Encoded = Utils.base64encode(ba, true);
                        int length = crlBase64Encoded.length();
                        int i = 0;
                        int j = 0;
                        int n = 1;

                        while (i < length) {
                            int k = crlBase64Encoded.indexOf('\n', i);
                            if (k < 0)
                                break;
                            if (n < 100) {
                                n++;
                                i = k + 1;
                            } else {
                                n = 1;
                                ArgBlock rarg = new ArgBlock();
                                rarg.addStringValue("crlBase64Encoded", crlBase64Encoded.substring(j, k));
                                i = k + 1;
                                j = i;
                                argSet.addRepeatRecord(rarg);
                            }
                        }
                        if (j < length) {
                            ArgBlock rarg = new ArgBlock();
                            rarg.addStringValue("crlBase64Encoded", crlBase64Encoded.substring(j, length));
                            argSet.addRepeatRecord(rarg);
                        }
                    } catch (CRLException e) {
                    }
                }
            }

            try {
                ServletOutputStream out = httpResp.getOutputStream();

                httpResp.setContentType("text/html");
                form.renderOutput(out, argSet);
                cmsReq.setStatus(CMSRequest.SUCCESS);
            } catch (IOException e) {
                logger.error(CMS.getLogMessage("CMSGW_ERR_OUT_STREAM_TEMPLATE", e.toString()), e);
                cmsReq.setError(new ECMSGWException(
                        CMS.getUserMessage("CMS_GW_DISPLAY_TEMPLATE_ERROR")));
                cmsReq.setStatus(CMSRequest.ERROR);
            }
            return;
        } else if (op.equals("importCRL") || op.equals("importDeltaCRL")) {
            if (clientIsMSIE(httpReq))
                mimeType = "application/pkix-crl";
            else
                mimeType = "application/x-pkcs7-crl";
        } else if (op.equals("getCRL")) {
            mimeType = "application/octet-stream";
            httpResp.setHeader("Content-disposition",
                    "attachment; filename=" + crlId + ".crl");
        } else if (op.equals("getDeltaCRL")) {
            mimeType = "application/octet-stream";
            httpResp.setHeader("Content-disposition",
                    "attachment; filename=delta-" + crlId + ".crl");
        } else {
            logger.error(CMS.getLogMessage("CMSGW_INVALID_OPTIONS_SELECTED"));
            throw new ECMSGWException(CMS.getUserMessage("CMS_GW_INVALID_OPTIONS_SELECTED"));
        }

        try {
            //            if (clientIsMSIE(httpReq) &&  op.equals("getCRL"))
            //                httpResp.setHeader("Content-disposition",
            //                  "attachment; filename=getCRL.crl");
            httpResp.setContentType(mimeType);
            httpResp.setContentLength(bytes.length);
            httpResp.getOutputStream().write(bytes);
            httpResp.getOutputStream().flush();
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERROR_DISPLAYING_CRLINFO"), e);
            throw new ECMSGWException(CMS.getUserMessage("CMS_GW_DISPLAYING_CRLINFO_ERROR"), e);
        }
        //		cmsReq.setResult(null);
        cmsReq.setStatus(CMSRequest.SUCCESS);
        return;
    }

    private String hexToDecimal(String hex) {
        String newHex = hex.substring(2);
        BigInteger bi = new BigInteger(newHex, 16);

        return bi.toString();
    }
}
