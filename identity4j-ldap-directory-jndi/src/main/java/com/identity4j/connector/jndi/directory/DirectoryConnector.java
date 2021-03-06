/* HEADER */
package com.identity4j.connector.jndi.directory;

/*
 * #%L
 * Idenity4J LDAP Directory JNDI
 * %%
 * Copyright (C) 2013 - 2017 LogonBox
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.naming.CommunicationException;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.net.SocketFactory;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.identity4j.connector.AbstractConnector;
import com.identity4j.connector.BrowseNode;
import com.identity4j.connector.BrowseableConnector;
import com.identity4j.connector.ConnectorCapability;
import com.identity4j.connector.ConnectorConfigurationParameters;
import com.identity4j.connector.PasswordCreationCallback;
import com.identity4j.connector.exception.ConnectorException;
import com.identity4j.connector.exception.InvalidLoginCredentialsException;
import com.identity4j.connector.exception.PrincipalNotFoundException;
import com.identity4j.connector.jndi.directory.LdapService.ResultMapper;
import com.identity4j.connector.principal.Identity;
import com.identity4j.connector.principal.Principal;
import com.identity4j.connector.principal.Role;
import com.identity4j.util.CollectionUtil;
import com.identity4j.util.StringUtil;
import com.identity4j.util.Util;
import com.identity4j.util.crypt.EncoderException;
import com.identity4j.util.crypt.impl.DefaultEncoderManager;

public class DirectoryConnector extends AbstractConnector implements BrowseableConnector {

	protected static final Iterator<Identity> IDENTITY_ITERATOR = CollectionUtil.emptyIterator(Identity.class);
	protected static final Iterator<Role> ROLE_ITERATOR = CollectionUtil.emptyIterator(Role.class);

	final static Log LOG = LogFactory.getLog(DirectoryConnector.class);

	public static final String WILDCARD_SEARCH = "*";
	public static final String OBJECT_CLASS_ATTRIBUTE = "objectClass";
	/**
	 */
	public static final String OU_ATTRIBUTE = "ou";
	/**
	 */
	public static final String COMMON_NAME_ATTRIBUTE = "cn";

	private static Collection<String> RESERVED_ATTRIBUTES_FOR_CREATION = Arrays
			.asList(new String[] { OU_ATTRIBUTE, OBJECT_CLASS_ATTRIBUTE, COMMON_NAME_ATTRIBUTE });

	private static Collection<String> CORE_IDENTITY_ATTRIBUTES = Arrays
			.asList(new String[] { COMMON_NAME_ATTRIBUTE, OBJECT_CLASS_ATTRIBUTE });

	private DirectoryConfiguration directoryConfiguration;
	protected LdapService ldapService;
	protected SocketFactory socketFactory;

	protected static Set<ConnectorCapability> capabilities = new HashSet<ConnectorCapability>(Arrays
			.asList(new ConnectorCapability[] { ConnectorCapability.passwordChange, ConnectorCapability.passwordSet,
					ConnectorCapability.createUser, ConnectorCapability.deleteUser, ConnectorCapability.updateUser,
					ConnectorCapability.roles, ConnectorCapability.authentication, ConnectorCapability.identities,
					ConnectorCapability.roleAttributes, ConnectorCapability.identityAttributes, }));

	public SocketFactory getSocketFactory() {
		return socketFactory;
	}

	public void setSocketFactory(SocketFactory socketFactory) {
		this.socketFactory = socketFactory;
		if (ldapService != null)
			ldapService.setSocketFactory(socketFactory);
	}

	@Override
	public void deleteIdentity(String principalName) throws ConnectorException {
		try {
			Identity identity = getIdentityByName(principalName);
			String identityOU = identity.getAttribute(getConfiguration().getDistinguishedNameAttribute());

			ldapService.unbind(new LdapName(identityOU));
		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException("Unreachable code");
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage(), e);
		}
	}

	@Override
	public void deleteRole(String roleName) throws ConnectorException {
		try {
			Role role = getRoleByName(roleName);
			String roleOU = role.getAttribute(getConfiguration().getDistinguishedNameAttribute());
			ldapService.unbind(new LdapName(roleOU));
		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException("Unreachable code");
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage(), e);
		}
	}

	@Override
	public Set<ConnectorCapability> getCapabilities() {
		return capabilities;
	}

	@Override
	public boolean isOpen() {
		return ldapService != null;
	}

	@Override
	protected void onClose() {
		ldapService.close();
		ldapService = null;
	}

	@Override
	public boolean isReadOnly() {
		return directoryConfiguration.getSecurityProtocol().equals(DirectoryConfiguration.PLAIN);
	}

	protected final DirectoryConfiguration getConfiguration() {
		return directoryConfiguration;
	}

	protected Name getRootDn() {
		final Name baseDn = getConfiguration().getBaseDn();
		for (int i = 0; i < baseDn.size(); i++) {
			if (!baseDn.get(i).toLowerCase().startsWith("dc=")) {
				final Name suffix = baseDn.getPrefix(i);
				return suffix;
			}
		}
		return (Name) baseDn.clone();
	}

	@Override
	public final Identity createIdentity(final Identity identity, final char[] password) throws ConnectorException {
		return createIdentity(identity, new PasswordCreationCallback() {
			@Override
			public char[] createPassword(Identity identity) {
				return password;
			}
		}, false);
	}

	@Override
	public final Identity createIdentity(final Identity identity, PasswordCreationCallback passwordCallback,
			boolean forceChange) throws ConnectorException {
		try {
			final DirectoryConfiguration config = getConfiguration();

			// OU may have been provided
			String identityOU = identity.getAttribute(OU_ATTRIBUTE);
			LdapName usersDn;
			if (StringUtil.isNullOrEmpty(identityOU)) {
				usersDn = new LdapName(getRootDn().toString());
				if (StringUtil.isNullOrEmpty(config.getOU())) {
					usersDn.add("CN=Users");
				} else {
					usersDn.add(config.getOU().toString());
				}
			} else {
				usersDn = new LdapName(identityOU);
			}

			LdapName userDn = new LdapName(usersDn.toString());
			String principalName = identity.getPrincipalName();

			if (StringUtils.isNotBlank(identity.getFullName())) {
				/*
				 * This connector declares itself as having Capbility.fullName, so it needs to
				 * support it.
				 */
				userDn.add("CN=" + identity.getFullName());
				identity.setFullName(identity.getFullName());
				identity.setAttribute("cn", identity.getFullName());
			} else if (StringUtils.isNotBlank(identity.getAttribute("givenName"))
					|| StringUtils.isNotBlank(identity.getAttribute("sn"))) {
				StringBuilder tmp = new StringBuilder();
				tmp.append(identity.getAttribute("givenName"));
				String initials = identity.getAttribute("initials");
				if (StringUtils.isNotBlank(initials)) {
					tmp.append(" ");
					tmp.append(initials);
					tmp.append(".");
				}
				tmp.append(" ");
				tmp.append(identity.getAttribute("sn"));

				userDn.add("CN=" + tmp.toString());
				identity.setFullName(tmp.toString());
				identity.setAttribute("cn", tmp.toString());
			} else {
				userDn.add("CN=" + identity.getPrincipalName());
				identity.setFullName(identity.getPrincipalName());
				identity.setAttribute("cn", identity.getPrincipalName());
			}

			Name baseDn = getConfiguration().getBaseDn();
			if (!userDn.toString().toLowerCase().endsWith(baseDn.toString().toLowerCase())) {
				throw new ConnectorException("The User DN (" + userDn + ") must be a child of the Base DN (" + baseDn
						+ " configured for the Active Directory connector.");
			}

			boolean included = getConfiguration().getIncludes().isEmpty();

			if (!included) {
				for (Name name : getConfiguration().getIncludes()) {
					if (userDn.startsWith(name)) {
						included = true;
					}
				}
			}

			if (included) {
				for (Name name : getConfiguration().getExcludes()) {
					if (userDn.startsWith(name)) {
						included = false;
					}
				}
			}

			if (!included) {
				throw new ConnectorException("The User DN (" + userDn
						+ ") must be within the included OU scopes configured for the Active Directory connector.");
			}
			/*
			 * Set up the attributes for the primary details. Some of these may already have
			 * been in the generic attributes
			 */
			List<Attribute> attributes = new ArrayList<Attribute>();

			// First copy in the generic attributes
			for (Map.Entry<String, String[]> entry : identity.getAttributes().entrySet()) {
				if (getCoreIdentityAttributes().contains(entry.getKey())) {
					continue;
				}
				String[] value = entry.getValue();
				if (value.length > 0 && !StringUtils.isEmpty(value[0])) {
					if (value.length == 1) {
						attributes.add(new BasicAttribute(entry.getKey(), value[0]));
					} else {
						Attribute attr = new BasicAttribute(entry.getKey());
						for (String val : value) {
							LOG.info("Setting " + entry.getKey() + " = " + val);
							attr.add(val);
						}
						attributes.add(attr);
					}
				}
			}

			BasicAttribute objectClassAttributeValues = new BasicAttribute(OBJECT_CLASS_ATTRIBUTE);
			for (String objectClass : getIdentityCreationObjectClasses(identity)) {
				objectClassAttributeValues.add(objectClass);
			}

			attributes.add(objectClassAttributeValues);
			attributes.add(new BasicAttribute(COMMON_NAME_ATTRIBUTE, identity.getFullName()));

			String upn = finaliseCreate(identity, principalName, attributes);

			ldapService.bind(userDn, attributes.toArray(new Attribute[0]));

			for (Role r : identity.getRoles()) {
				assignRole(userDn, r);
			}

			DirectoryIdentity directoryIdentity = (DirectoryIdentity) getIdentityByName(upn);

			ldapService.setPassword(userDn.toString(), passwordCallback.createPassword(directoryIdentity));
			if (getCapabilities().contains(ConnectorCapability.accountDisable))
				enableIdentity(directoryIdentity);
			finaliseCreate(directoryIdentity, forceChange);

			return directoryIdentity;

		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException("Unreachable code");
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage(), e);
		}
	}

	protected List<String> getIdentityCreationObjectClasses(Identity identity) {
		return getConfiguration().getIdentityCreationObjectClasses();
	}

	protected void finaliseCreate(DirectoryIdentity directoryIdentity, boolean forceChange) {
	}

	protected String finaliseCreate(Identity identity, String principalName, List<Attribute> attributes) {
		String upn = identity.getAttribute(directoryConfiguration.getIdentityNameAttribute());
		if (StringUtils.isBlank(upn))
			upn = principalName;
		attributes.add(new BasicAttribute(directoryConfiguration.getIdentityNameAttribute(), upn));
		return upn;
	}

	protected Collection<String> getCoreIdentityAttributes() {
		return CORE_IDENTITY_ATTRIBUTES;
	}

	protected void assignRole(LdapName userDn, Role role) throws NamingException, IOException {
	}

	protected String processNamingException(NamingException nme, PasswordResetType type, Principal pricipal) {
		return getReason(nme);
	}

	protected void checkOtherCreationExceptions(Identity identity, Exception e) {
	}

	protected void checkNamingException(String errorText, NamingException nme, Principal principal)
			throws ConnectorException {
		processNamingException(nme, null, principal);
		DirectoryExceptionParser dep = new DirectoryExceptionParser(nme);
		String reason = dep.getReason();
		LOG.error(errorText + ". Reason code give was " + reason, nme);
		throw new ConnectorException(
				"Failed to perform operation. Reason code " + reason + ". Please see the logs for more detail.");
	}

	protected Collection<String> getAttributesReservedForCreation() {
		return RESERVED_ATTRIBUTES_FOR_CREATION;
	}

	protected List<ModificationItem> getCreationPasswordModificationItems(char[] password,
			final DirectoryConfiguration config) throws EncoderException {
		List<ModificationItem> items = new ArrayList<ModificationItem>();
		byte[] encodedPassword = DefaultEncoderManager.getInstance().encode(password,
				config.getIdentityPasswordEncoding(), "UTF-8", null, null);
		Attribute attribute = new BasicAttribute(config.getIdentityPasswordAttribute(), encodedPassword);
		items.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attribute));
		return items;
	}

	@Override
	public boolean supportsOptimisedCheckCredentials() {
		return true;
	}

	@Override
	public boolean checkCredentialsOptimised(String username, String remoteIdentifier, char[] password)
			throws ConnectorException {
		try {
			ldapService.authenticate(remoteIdentifier, new String(password));
			return true;
		} catch (IOException e) {
			return false;
		} catch (NamingException e) {
			processNamingException(e);
			return false;
		}
	}

	@Override
	protected boolean areCredentialsValid(Identity identity, char[] password) throws ConnectorException {

		DirectoryIdentity directoryIdentity = (DirectoryIdentity) identity;
		try {
			ldapService.authenticate(directoryIdentity.getDn().toString(), new String(password));
			return true;
		} catch (IOException e) {
			return false;
		} catch (NamingException e) {
			try {
				processNamingException(e);
			} catch (InvalidLoginCredentialsException ilce) {
			}
			return false;
		}
	}

	@Override
	protected void changePassword(Identity identity, char[] oldPassword, char[] password) {
		DirectoryIdentity directoryIdentity = (DirectoryIdentity) identity;
		try {
			ldapService.setPassword(directoryIdentity.getDn().toString(), password);
		} catch (NamingException e) {
			LOG.error("Problem in changing password.", e);
		} catch (IOException e) {
			LOG.error("Problem in changing password.", e);
		}
	}

	protected boolean isIncluded(String dn) throws InvalidNameException {
		return isIncluded(new LdapName(dn));
	}
	
	protected boolean isIncluded(Name dn) {
		Name baseDn = getConfiguration().getBaseDn();
		if (!dn.toString().toLowerCase().endsWith(baseDn.toString().toLowerCase())) {
			return false;
		}

		boolean included = getConfiguration().getIncludes().isEmpty();

		if (!included) {
			for (Name name : getConfiguration().getIncludes()) {
				if (dn.startsWith(name)) {
					included = true;
				}
			}
		}

		if (included) {
			for (Name name : getConfiguration().getExcludes()) {
				if (dn.startsWith(name)) {
					included = false;
				}
			}
		}

		return included;
	}
	
	@Override
	public void updateIdentity(final Identity identity) throws ConnectorException {

		try {
			List<ModificationItem> modificationItems = new ArrayList<ModificationItem>();
			Identity oldIdentity = getIdentityByName(identity.getPrincipalName());

			final DirectoryConfiguration config = getConfiguration();

			// OU may have been provided
			String identityOU = identity.getAttribute(config.getDistinguishedNameAttribute());
			LdapName usersDn = new LdapName(identityOU);

			processUserAttributes(modificationItems, oldIdentity, identity);

			if (!modificationItems.isEmpty()) {
				ldapService.update(usersDn, modificationItems.toArray(new ModificationItem[0]));
			}

			// Update roles
			List<Role> toRemove = new ArrayList<Role>(Arrays.asList(oldIdentity.getRoles()));
			List<Role> toAdd = new ArrayList<Role>(Arrays.asList(identity.getRoles()));
			toRemove.removeAll(Arrays.asList(identity.getRoles()));
			toAdd.removeAll(Arrays.asList(oldIdentity.getRoles()));

			for (Role r : toRemove) {
				if(isIncluded(r.getAttribute(getConfiguration().getDistinguishedNameAttribute()))) {
					revokeRole(usersDn, r);
				}
			}

			for (Role r : toAdd) {
				assignRole(usersDn, r);
			}

			if (Util.differs(oldIdentity.getFullName(), identity.getFullName())) {
				LdapName newDN = new LdapName(usersDn.getSuffix(1).toString());
				newDN.add(0, "CN=" + identity.getFullName());
				ldapService.rename(usersDn, newDN);
			} else if (Util.differs(oldIdentity.getAttribute(COMMON_NAME_ATTRIBUTE),
					identity.getAttribute(COMMON_NAME_ATTRIBUTE))) {
				LdapName newDN = new LdapName(usersDn.toString());
				newDN.remove(newDN.size() - 1);
				newDN.add(newDN.size(), "CN=" + identity.getAttribute(COMMON_NAME_ATTRIBUTE));
				ldapService.rename(usersDn, newDN);
			} else if (Util.differs(oldIdentity.getAttribute(OU_ATTRIBUTE), identity.getAttribute(OU_ATTRIBUTE))) {
				LdapName newDN = new LdapName("CN=" + identity.getAttribute(COMMON_NAME_ATTRIBUTE) + ","
						+ identity.getAttribute(OU_ATTRIBUTE));
				ldapService.rename(usersDn, newDN);
			}

		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException("Unreachable code");
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage(), e);
		}

	}

	protected boolean isExcludeForUserUpdate(String attributeName) {
		return false;
	}

	protected void processUserAttributes(List<ModificationItem> modificationItems, Identity previousState,
			Identity newState) {

		for (Map.Entry<String, String[]> entry : newState.getAttributes().entrySet()) {
			if (!isExcludeForUserUpdate(entry.getKey())) {
				if (!previousState.getAttributes().containsKey(entry.getKey())) {
					// New
					if (entry.getValue().length > 0) {

						String[] value = entry.getValue();
						if (value.length > 0 && !StringUtils.isEmpty(value[0])) {
							Attribute attr = new BasicAttribute(entry.getKey());
							for (String val : value) {
								attr.add(val);
							}
							modificationItems.add(new ModificationItem(DirContext.ADD_ATTRIBUTE, attr));
						}
					}
				} else {
					String[] oldValue = previousState.getAttributes().get(entry.getKey());
					String[] newValue = newState.getAttributes().get(entry.getKey());
					if (!ArrayUtils.isEquals(oldValue, newValue)) {

						String[] value = entry.getValue();
						if (value.length > 0 && !StringUtils.isEmpty(value[0])) {
							Attribute attr = new BasicAttribute(entry.getKey());
							for (String val : value) {
								attr.add(val);
							}
							modificationItems.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attr));
						} else {
							Attribute attr = new BasicAttribute(entry.getKey());
							modificationItems.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, attr));
						}
					}
				}
			}
		}
	}

	protected void revokeRole(LdapName userDn, Role role) throws NamingException, IOException {
	}

	@Override
	protected void setPassword(Identity identity, char[] password, boolean forcePasswordChangeAtLogon,
			PasswordResetType type) throws ConnectorException {
		DirectoryIdentity directoryIdentity = (DirectoryIdentity) identity;
		try {
			ldapService.setPassword(directoryIdentity.getDn().toString(), password);
		} catch (NamingException e) {
			LOG.error("Problem in getting identities.", e);
		} catch (IOException e) {
			LOG.error("Problem in getting identities.", e);
		}
	}

	public Iterator<DirectoryOU> getOrganizationalUnits() throws ConnectorException, IOException {
		try {
			return ldapService.search(ldapService.buildObjectClassFilter("organizationalUnit", "ou", WILDCARD_SEARCH),
					new ResultMapper<DirectoryOU>() {

						@Override
						public DirectoryOU apply(SearchResult result) throws NamingException {
							return new DirectoryOU((String) result.getAttributes().get("distinguishedName").get(),
									(String) result.getAttributes().get("ou").get());
						}

						public boolean isApplyFilters() {
							return true;
						}
					}, ldapService.getSearchControls());
		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	@Override
	public final Identity getIdentityByName(String identityName) throws PrincipalNotFoundException, ConnectorException {
		String identityFilter = buildIdentityFilter(identityName);
		return getPrincipal(identityFilter, getIdentities(identityFilter));
	}

	public final Iterator<Identity> allIdentities() throws ConnectorException {
		return getIdentities(buildIdentityFilter(WILDCARD_SEARCH));
	}

	@Override
	public long countIdentities() throws ConnectorException {
		// There is no method for generic LDAP so it's better to return nothing
		return -1;
	}

	@Override
	public long countRoles() throws ConnectorException {
		// There is no method for generic LDAP so it's better to return nothing
		return -1;
	}

	protected String buildIdentityFilter(String identityName) {
		String identityObjectClass = directoryConfiguration.getIdentityObjectClass();
		String identityNameAttribute = directoryConfiguration.getIdentityNameAttribute();
		return ldapService.buildObjectClassFilter(identityObjectClass, identityNameAttribute, identityName);
	}

	protected Iterator<Identity> getIdentities(String filter) {
		try {
			return ldapService.search(filter, new ResultMapper<Identity>() {

				public Identity apply(SearchResult result) throws NamingException {
					return mapIdentity(result);
				}

				public boolean isApplyFilters() {
					return true;
				}
			}, configureSearchControls(ldapService.getSearchControls()));
		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException("Unreachable code");
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage(), e);
		}
	}

	protected Identity mapIdentity(SearchResult result) throws NamingException {
		Attributes attributes = result.getAttributes();
		String guid = StringUtil
				.nonNull(attributes.get(directoryConfiguration.getIdentityGuidAttribute()).get().toString());
		String identityName = StringUtil
				.nonNull(attributes.get(directoryConfiguration.getIdentityNameAttribute()).get().toString());
		LdapName dn = new LdapName(result.getName().toString());
		Name base = directoryConfiguration.getBaseDn();
		for (int i = base.size() - 1; i >= 0; i--) {
			dn.add(0, base.get(i));
		}

		NamingEnumeration<? extends Attribute> ne = attributes.getAll();
		DirectoryIdentity directoryIdentity = new DirectoryIdentity(guid, identityName, dn);
		directoryIdentity.setAttribute("dn", dn.toString());
		while (ne.hasMoreElements()) {
			Attribute a = ne.next();
			if (!a.getID().equals(directoryConfiguration.getIdentityGuidAttribute())
					&& !a.getID().equals(directoryConfiguration.getIdentityNameAttribute())) {
				List<String> vals = new ArrayList<String>();
				NamingEnumeration<?> ane = a.getAll();
				while (ane.hasMoreElements()) {
					Object val = ane.next();
					vals.add(val == null ? null : String.valueOf(val));
				}
				directoryIdentity.setAttribute(a.getID(), vals.toArray(new String[0]));
			}
		}

		String idRoleAttr = directoryConfiguration.getIdentityRoleGuidAttribute();
		if (!StringUtil.isNullOrEmpty(idRoleAttr)) {
			String roleObjectClass = directoryConfiguration.getRoleObjectClass();
			String roleNameAttribute = directoryConfiguration.getRoleGuidAttribute();
			String filter = ldapService.buildObjectClassFilter(roleObjectClass, roleNameAttribute,
					attributes.get(idRoleAttr).get().toString());
			directoryIdentity.addRole(getPrincipal(filter, getRoles(filter)));
		} else {
			idRoleAttr = directoryConfiguration.getIdentityRoleNameAttribute();
			if (!StringUtil.isNullOrEmpty(idRoleAttr)) {
				directoryIdentity.addRole(getRoleByName(idRoleAttr));
			}
		}

		return directoryIdentity;
	}

	@Override
	public final Role getRoleByName(String roleName) throws PrincipalNotFoundException, ConnectorException {
		if (!getConfiguration().isEnableRoles()) {
			throw new PrincipalNotFoundException("Roles are not enabled");
		}
		String roleNameFilter = buildRoleFilter(roleName, false);
		return getPrincipal(roleNameFilter, getRoles(roleNameFilter));
	}

	public final Iterator<Role> allRoles() throws ConnectorException {
		if (!getConfiguration().isEnableRoles()) {
			return ROLE_ITERATOR;
		}
		return getRoles(buildRoleFilter(WILDCARD_SEARCH, true));
	}

	private String buildRoleFilter(String roleName, boolean isWildcard) {
		String roleObjectClass = directoryConfiguration.getRoleObjectClass();
		String roleNameAttribute = directoryConfiguration.getRoleNameAttribute();
		return ldapService.buildObjectClassFilter(roleObjectClass, roleNameAttribute, roleName);
	}

	protected Iterator<Role> getRoles() {
		return getRoles(buildRoleFilter(WILDCARD_SEARCH, true));
	}

	protected Iterator<Role> getRoles(String filter) {
		try {
			return ldapService.search(filter, new ResultMapper<Role>() {

				public Role apply(SearchResult result) throws NamingException {
					return mapRole(result);
				}

				public boolean isApplyFilters() {
					return true;
				}
			}, configureRoleSearchControls(ldapService.getSearchControls()));

		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException("Unreachable code");
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage(), e);
		}

	}

	protected Role mapRole(SearchResult result) throws NamingException {
		Attributes attributes = result.getAttributes();
		String guid = StringUtil
				.nonNull(attributes.get(directoryConfiguration.getRoleGuidAttribute()).get().toString());
		String identityName = StringUtil
				.nonNull(attributes.get(directoryConfiguration.getRoleNameAttribute()).get().toString());
		LdapName dn = new LdapName(result.getName().toString());
		NamingEnumeration<? extends Attribute> ne = attributes.getAll();
		DirectoryRole directoryRole = new DirectoryRole(guid, identityName, dn);
		while (ne.hasMoreElements()) {
			Attribute a = ne.next();
			if (!a.getID().equals(directoryConfiguration.getIdentityGuidAttribute())
					&& !a.getID().equals(directoryConfiguration.getIdentityNameAttribute())) {
				List<String> vals = new ArrayList<String>();
				NamingEnumeration<?> ane = a.getAll();
				while (ane.hasMoreElements()) {
					Object val = ane.next();
					vals.add(val == null ? null : String.valueOf(val));
				}
				directoryRole.setAttribute(a.getID(), vals.toArray(new String[0]));
			}
		}
		return directoryRole;
	}

	protected SearchControls configureSearchControls(SearchControls searchControls) {
		searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		// searchControls.setCountLimit(0);
		searchControls.setReturningObjFlag(true);
		return searchControls;
	}

	protected SearchControls configureRoleSearchControls(SearchControls searchControls) {
		searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		// searchControls.setCountLimit(0);
		searchControls.setReturningObjFlag(true);
		return searchControls;
	}

	protected final String getAttributeValue(Name dn, String attributeName) {

		try {
			LdapContext ctx = ldapService.lookupContext(dn);
			try {
				Attributes attributes = ctx.getAttributes("");
				return attributes.get(attributeName) != null ? attributes.get(attributeName).get().toString() : null;
			} catch (NamingException e) {
				processNamingException(e);
				throw new IllegalStateException("Unreachable code");
			} finally {
				ctx.close();
			}
		} catch (NamingException ex) {
			processNamingException(ex);
			throw new IllegalStateException("Unreachable code");
		} catch (IOException ex) {
			throw new IllegalStateException(ex.getMessage(), ex);
		}
	}

	protected final String getByteValue(String attributeName, Attributes attributes) {
		try {
			byte[] objectGuid = (byte[]) attributes.get(attributeName).get();
			if (objectGuid == null) {
				throw new IllegalArgumentException(attributeName + " cannot be null");
			}
			return StringUtil.convertByteToString(objectGuid);
		} catch (NamingException e) {
			processNamingException(e);
			throw new IllegalStateException("Unreachable code");
		}
	}

	@Override
	protected void onOpen(ConnectorConfigurationParameters parameters) {
		directoryConfiguration = (DirectoryConfiguration) parameters;

		try {
			ldapService = new LdapService();
			ldapService.setSocketFactory(socketFactory);
			ldapService.init(directoryConfiguration);
			ldapService.openConnection();
			Name baseDn = directoryConfiguration.getBaseDn();
			LOG.info("Looking up " + baseDn);

		} catch (NamingException nme) {
			ldapService = null;
			processNamingException(nme);
		} catch (Exception e) {
			ldapService = null;
			throw new ConnectorException(e);
		}
	}

	protected String processNamingException(NamingException nme) {
		if (nme instanceof CommunicationException) {
			if(nme.getRootCause() != null && nme.getRootCause().getClass().getName().equals("com.hypersocket.certificates.CertificateVerificationException")) {
				throw (RuntimeException)nme.getCause();
			}
			throw new ConnectorException(
					String.format("Failed to connect to %s", directoryConfiguration.getControllerHostnames()[0]), nme);
		}
		DirectoryExceptionParser dep = new DirectoryExceptionParser(nme);
		if (dep.getCode() == 49)
			throw new InvalidLoginCredentialsException();
		String message = dep.getMessage();
		throw new ConnectorException(message, nme);
	}

	protected String getReason(NamingException nme) {
		/*
		 * This is a bit crap. There must be a better way of getting at the codes? Also,
		 * are they AD specific?
		 */
		String message = getMessage(nme);
		if (!StringUtil.isNullOrEmpty(message)) {
			final String string = "LDAP: error code ";
			int ldpx = message.indexOf(string);
			if (ldpx != -1) {
				String err = message.substring(ldpx + string.length());
				StringTokenizer t = new StringTokenizer(err);
				t.nextToken();
				t.nextToken();
				String reason = t.nextToken();
				while (reason.endsWith(":")) {
					reason = reason.substring(0, reason.length() - 1);
				}
				return reason;
			}
		}
		return "Unknown reason";
	}

	protected int getCode(NamingException nme) {
		/*
		 * This is a bit crap. There must be a better way of getting at the codes? Also,
		 * are they AD specific?
		 */
		String message = getMessage(nme);
		if (!StringUtil.isNullOrEmpty(message)) {
			final String string = "LDAP: error code ";
			int ldpx = message.indexOf(string);
			if (ldpx != -1) {
				String err = message.substring(ldpx + string.length());
				StringTokenizer t = new StringTokenizer(err);
				return Integer.parseInt(t.nextToken());
			}
		}
		return 0;
	}

	protected String getMessage(NamingException nme) {
		String message = nme.getExplanation();
		if (StringUtil.isNullOrEmpty(message)) {
			return StringUtil.isNullOrEmpty(nme.getMessage()) ? "No actual error message supplied." : nme.getMessage();
		}
		if (message.startsWith("[")) {
			message = message.substring(1);
		}
		if (message.endsWith("]")) {
			message = message.substring(0, message.length() - 1);
		}
		return message;
	}

	@Override
	public Iterator<BrowseNode> getBrowseableNodes(BrowseNode parent) {
		final SearchControls ctrls = new SearchControls();
		ctrls.setSearchScope(SearchControls.OBJECT_SCOPE);
		ctrls.setReturningObjFlag(true);

		try {
			Iterator<List<BrowseNode>> nodes = ldapService.search(new LdapName(parent.toString()), "(objectclass=*)",
					new ResultMapper<List<BrowseNode>>() {

						@SuppressWarnings("serial")
						public List<BrowseNode> apply(SearchResult result) throws NamingException {

							Attribute namingContexts = result.getAttributes().get("namingContexts");
							if (namingContexts == null)
								return Collections.emptyList();

							@SuppressWarnings("rawtypes")
							final NamingEnumeration enumeration = namingContexts.getAll();

							List<BrowseNode> l = new ArrayList<BrowseNode>();
							while (enumeration.hasMore()) {
								final String node = (String) enumeration.next();
								l.add(new BrowseNode() {
									@Override
									public boolean isLeaf() {
										return false;
									}

									public String toString() {
										return node;
									}

								});
							}
							return l;

						}

						public boolean isApplyFilters() {
							return true;
						}
					}, ldapService.getSearchControls());

			return nodes.hasNext() ? nodes.next().iterator() : new ArrayList<BrowseNode>().iterator();
		} catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

		// Collection<? extends Name> dns = Collections.singleton(new
		// DistinguishedName(""));
		// Iterator<List<BrowseNode>> nodes = new
		// SearchResultsIterator<List<BrowseNode>>(s, dns, "(objectclass=*)",
		// ctrls);
		// return nodes.hasNext() ? nodes.next().iterator() : new
		// ArrayList<BrowseNode>().iterator();

		// Iterator<List<BrowseNode>> nodes = ldapTemplate.search("", "(objectclass=*)",
		// ctrls, s, null).iterator();
		// return nodes.hasNext() ? nodes.next().iterator() : new
		// ArrayList<BrowseNode>().iterator();
		// return null;

	}

}