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
// (C) 2017 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---

package com.netscape.cms.authentication;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

import org.dogtagpki.server.authentication.AuthManagerConfig;
import org.dogtagpki.server.authentication.AuthToken;

import com.netscape.certsrv.authentication.AuthCredentials;
import com.netscape.certsrv.authentication.EMissingCredential;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.SessionContext;
import com.netscape.certsrv.property.IDescriptor;
import com.netscape.cms.profile.ProfileAuthenticator;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ConfigStore;
import com.netscape.cmscore.request.Request;

/**
 * Pull any existing auth token from the session context.
 *
 * Use with caution as a profile authenticator; if there is a
 * session it will unconditionally approve the request
 * (subject to constraints, etc).
 */
public class SessionAuthentication
        implements ProfileAuthenticator {

    private String instName = null;
    private String implName = null;
    private AuthManagerConfig config;

    public SessionAuthentication() {
    }

    @Override
    public void init(String instName, String implName, AuthManagerConfig config)
            throws EBaseException {
        this.instName = instName;
        this.implName = implName;
        this.config = config;
    }

    /**
     * Gets the name of this authentication manager.
     */
    @Override
    public String getName() {
        return instName;
    }

    /**
     * Gets the plugin name of authentication manager.
     */
    @Override
    public String getImplName() {
        return implName;
    }

    @Override
    public boolean isSSLClientRequired() {
        return false;
    }

    /**
     * Authenticate user.
     *
     * @return the auth token from existing session context, if any.
     * @throws EMissingCredential if no auth token or no session
     */
    @Override
    public AuthToken authenticate(AuthCredentials authCred)
            throws EMissingCredential {
        SessionContext context = SessionContext.getExistingContext();

        if (context == null)
            throw new EMissingCredential("SessionAuthentication: no session");

        AuthToken authToken = (AuthToken) context.get(SessionContext.AUTH_TOKEN);

        if (authToken == null)
            throw new EMissingCredential("SessionAuthentication: no auth token");

        return authToken;
    }

    @Override
    public String[] getRequiredCreds() {
        String[] requiredCreds = { };
        return requiredCreds;
    }

    @Override
    public String[] getConfigParams() {
        return null;
    }

    /**
     * prepare this authentication manager for shutdown.
     */
    @Override
    public void shutdown() {
    }

    /**
     * gets the configuretion substore used by this authentication
     * manager
     *
     * @return configuration store
     */
    @Override
    public AuthManagerConfig getConfigStore() {
        return config;
    }

    // Profile-related methods

    @Override
    public void init(ConfigStore config) {
    }

    /**
     * Retrieves the localizable name of this policy.
     */
    @Override
    public String getName(Locale locale) {
        return CMS.getUserMessage(locale, "CMS_AUTHENTICATION_AGENT_NAME");
    }

    /**
     * Retrieves the localizable description of this policy.
     */
    @Override
    public String getText(Locale locale) {
        return CMS.getUserMessage(locale, "CMS_AUTHENTICATION_AGENT_TEXT");
    }

    /**
     * Retrieves a list of names of the value parameter.
     */
    @Override
    public Enumeration<String> getValueNames() {
        return Collections.emptyEnumeration();
    }

    @Override
    public boolean isValueWriteable(String name) {
        return false;
    }

    /**
     * Retrieves the descriptor of the given value
     * parameter by name.
     */
    @Override
    public IDescriptor getValueDescriptor(Locale locale, String name) {
        return null;
    }

    @Override
    public void populate(AuthToken token, Request request) {
    }
}
