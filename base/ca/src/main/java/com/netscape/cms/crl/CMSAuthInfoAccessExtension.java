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
package com.netscape.cms.crl;

import java.io.IOException;

import org.dogtagpki.server.ca.CAEngine;
import org.dogtagpki.server.ca.CAEngineConfig;
import org.dogtagpki.server.ca.ICMSCRLExtension;
import org.mozilla.jss.netscape.security.extensions.AuthInfoAccessExtension;
import org.mozilla.jss.netscape.security.util.ObjectIdentifier;
import org.mozilla.jss.netscape.security.x509.Extension;
import org.mozilla.jss.netscape.security.x509.GeneralName;
import org.mozilla.jss.netscape.security.x509.URIName;
import org.mozilla.jss.netscape.security.x509.X500Name;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.EPropertyNotFound;
import com.netscape.certsrv.base.IExtendedPluginInfo;
import com.netscape.certsrv.common.NameValuePairs;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ConfigStore;

/**
 * This represents a Authority Information Access CRL extension.
 *
 * @version $Revision$, $Date$
 */
public class CMSAuthInfoAccessExtension
        implements ICMSCRLExtension, IExtendedPluginInfo {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CMSAuthInfoAccessExtension.class);

    public static final String PROP_NUM_ADS = "numberOfAccessDescriptions";
    public static final String PROP_ACCESS_METHOD = "accessMethod";
    public static final String PROP_ACCESS_LOCATION_TYPE = "accessLocationType";
    public static final String PROP_ACCESS_LOCATION = "accessLocation";

    private static final String PROP_ACCESS_METHOD_OCSP = "ocsp";
    private static final String PROP_ACCESS_METHOD_CAISSUERS = "caIssuers";
    private static final String PROP_DIRNAME = "DirectoryName";
    private static final String PROP_URINAME = "URI";

    public CMSAuthInfoAccessExtension() {
    }

    @Override
    public Extension setCRLExtensionCriticality(Extension ext,
            boolean critical) {
        AuthInfoAccessExtension authInfoAccessExt = (AuthInfoAccessExtension) ext;

        authInfoAccessExt.setCritical(critical);

        return authInfoAccessExt;
    }

    @Override
    public Extension getCRLExtension(ConfigStore config, Object ip, boolean critical) {
        AuthInfoAccessExtension authInfoAccessExt = new AuthInfoAccessExtension(critical);

        int numberOfAccessDescriptions = 0;

        try {
            numberOfAccessDescriptions = config.getInteger(PROP_NUM_ADS, 0);
        } catch (EBaseException e) {
            logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_INVALID_NUM_ADS", e.toString()), e);
        }

        if (numberOfAccessDescriptions > 0) {

            for (int i = 0; i < numberOfAccessDescriptions; i++) {
                String accessMethod = null;
                String accessLocationType = null;
                String accessLocation = null;
                ObjectIdentifier method = AuthInfoAccessExtension.METHOD_CA_ISSUERS;

                try {
                    accessMethod = config.getString(PROP_ACCESS_METHOD + i);

                } catch (EPropertyNotFound e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_AM_UNDEFINED", e.toString()), e);

                } catch (EBaseException e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_AM_INVALID", e.toString()), e);
                }

                if (accessMethod != null && accessMethod.equals(PROP_ACCESS_METHOD_OCSP)) {
                    method = AuthInfoAccessExtension.METHOD_OCSP;
                }

                try {
                    accessLocationType = config.getString(PROP_ACCESS_LOCATION_TYPE + i);

                } catch (EPropertyNotFound e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_ALT_UNDEFINED", e.toString()), e);

                } catch (EBaseException e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_ALT_INVALID", e.toString()), e);
                }

                try {
                    accessLocation = config.getString(PROP_ACCESS_LOCATION + i);

                } catch (EPropertyNotFound e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_DIST_POINT_UNDEFINED", e.toString()), e);

                } catch (EBaseException e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_DIST_POINT_INVALID", e.toString()), e);
                }

                if (accessLocationType != null && accessLocation != null && accessLocation.length() > 0) {
                    if (accessLocationType.equalsIgnoreCase(PROP_DIRNAME)) {
                        try {
                            X500Name dirName = new X500Name(accessLocation);
                            authInfoAccessExt.addAccessDescription(method, new GeneralName(dirName));
                        } catch (IOException e) {
                            logger.warn(CMS.getLogMessage("CRL_CREATE_INVALID_500NAME", e.toString()), e);
                        }
                    } else if (accessLocationType.equalsIgnoreCase(PROP_URINAME)) {
                        URIName uriName = new URIName(accessLocation);
                        authInfoAccessExt.addAccessDescription(method, new GeneralName(uriName));
                    } else {
                        logger.warn(CMS.getLogMessage("CRL_INVALID_POTINT_TYPE", accessLocation));
                    }
                } else {
                    accessLocationType = PROP_URINAME;
                    CAEngine engine = CAEngine.getInstance();
                    CAEngineConfig cs = engine.getConfig();
                    String hostname = cs.getHostname();
                    String port = engine.getEENonSSLPort();
                    if (hostname != null && port != null) {
                        accessLocation = "http://" + hostname + ":" + port + "/ca/ee/ca/getCAChain?op=downloadBIN";
                    }
                    URIName uriName = new URIName(accessLocation);
                    authInfoAccessExt.addAccessDescription(AuthInfoAccessExtension.METHOD_CA_ISSUERS, new GeneralName(
                            uriName));
                }
            }
        }

        return authInfoAccessExt;
    }

    @Override
    public String getCRLExtOID() {
        return AuthInfoAccessExtension.ID.toString();
    }

    @Override
    public void getConfigParams(ConfigStore config, NameValuePairs nvp) {

        int numberOfAccessDescriptions = 0;

        try {
            numberOfAccessDescriptions = config.getInteger(PROP_NUM_ADS, 0);
        } catch (EBaseException e) {
            logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_INVALID_NUM_ADS", e.toString()), e);
        }

        nvp.put(PROP_NUM_ADS, String.valueOf(numberOfAccessDescriptions));

        for (int i = 0; i < numberOfAccessDescriptions; i++) {
            String accessMethod = null;
            String accessLocationType = null;
            String accessLocation = null;

            try {
                accessMethod = config.getString(PROP_ACCESS_METHOD + i);

            } catch (EPropertyNotFound e) {
                logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_AM_UNDEFINED", e.toString()), e);

            } catch (EBaseException e) {
                logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_AM_INVALID", e.toString()), e);
            }

            if (accessMethod != null && accessMethod.length() > 0) {
                nvp.put(PROP_ACCESS_METHOD + i, accessMethod);
            } else {
                nvp.put(PROP_ACCESS_METHOD + i, PROP_ACCESS_METHOD_CAISSUERS);
            }

            try {
                accessLocationType = config.getString(PROP_ACCESS_LOCATION_TYPE + i);

            } catch (EPropertyNotFound e) {
                logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_ALT_UNDEFINED", e.toString()), e);

            } catch (EBaseException e) {
                logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_ALT_INVALID", e.toString()), e);
            }

            if (accessLocationType != null && accessLocationType.length() > 0) {
                nvp.put(PROP_ACCESS_LOCATION_TYPE + i, accessLocationType);
            } else {
                nvp.put(PROP_ACCESS_LOCATION_TYPE + i, PROP_URINAME);
            }

            try {
                accessLocation = config.getString(PROP_ACCESS_LOCATION + i);

            } catch (EPropertyNotFound e) {
                logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_AL_UNDEFINED", e.toString()), e);

            } catch (EBaseException e) {
                logger.warn(CMS.getLogMessage("CRL_CREATE_AIA_AD_AL_INVALID", e.toString()), e);
            }

            if (accessLocation != null && accessLocation.length() > 0) {
                nvp.put(PROP_ACCESS_LOCATION + i, accessLocation);
            } else {
                CAEngine engine = CAEngine.getInstance();
                CAEngineConfig cs = engine.getConfig();
                String hostname = cs.getHostname();
                String port = engine.getEENonSSLPort();
                if (hostname != null && port != null) {
                    accessLocation = "http://" + hostname + ":" + port + "/ca/ee/ca/getCAChain?op=downloadBIN";
                }
                nvp.put(PROP_ACCESS_LOCATION + i, accessLocation);
            }
        }
    }

    @Override
    public String[] getExtendedPluginInfo() {
        String[] params = {
                "enable;boolean;Check to enable Authority Information Access extension.",
                "critical;boolean;Set criticality for Authority Information Access extension.",
                PROP_NUM_ADS + ";number;Set number of Access Descriptions.",
                PROP_ACCESS_METHOD + "0;choice(" + PROP_ACCESS_METHOD_CAISSUERS + "," +
                        PROP_ACCESS_METHOD_OCSP + ");Select access description method.",
                PROP_ACCESS_LOCATION_TYPE + "0;choice(" + PROP_URINAME + "," +
                        PROP_DIRNAME + ");Select access location type.",
                PROP_ACCESS_LOCATION + "0;string;Enter access location " +
                        "corresponding to the selected access location type.",
                IExtendedPluginInfo.HELP_TOKEN +
                        ";configuration-ca-edit-crlextension-authorityinformationaccess",
                PROP_ACCESS_METHOD + "1;choice(" + PROP_ACCESS_METHOD_CAISSUERS + "," +
                        PROP_ACCESS_METHOD_OCSP + ");Select access description method.",
                PROP_ACCESS_LOCATION_TYPE + "1;choice(" + PROP_URINAME + "," +
                        PROP_DIRNAME + ");Select access location type.",
                PROP_ACCESS_LOCATION + "1;string;Enter access location " +
                        "corresponding to the selected access location type.",
                IExtendedPluginInfo.HELP_TOKEN +
                        ";configuration-ca-edit-crlextension-authorityinformationaccess",
                PROP_ACCESS_METHOD + "2;choice(" + PROP_ACCESS_METHOD_CAISSUERS + "," +
                        PROP_ACCESS_METHOD_OCSP + ");Select access description method.",
                PROP_ACCESS_LOCATION_TYPE + "2;choice(" + PROP_URINAME + "," +
                        PROP_DIRNAME + ");Select access location type.",
                PROP_ACCESS_LOCATION + "2;string;Enter access location " +
                        "corresponding to the selected access location type.",
                IExtendedPluginInfo.HELP_TOKEN +
                        ";configuration-ca-edit-crlextension-authorityinformationaccess",
                IExtendedPluginInfo.HELP_TEXT +
                        ";The Freshest CRL is a non critical CRL extension " +
                        "that identifies the delta CRL distribution points for a particular CRL."
            };

        return params;
    }
}
