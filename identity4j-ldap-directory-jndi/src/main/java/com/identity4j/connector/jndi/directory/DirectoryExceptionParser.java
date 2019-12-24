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

import java.util.StringTokenizer;

import javax.naming.NamingException;

import com.identity4j.util.StringUtil;

public class DirectoryExceptionParser {

	private String reason = "Unknown";
	private String data = "";
	private int code;
	private String ldapError;
	private String message;

	public DirectoryExceptionParser(NamingException nme) {
		parse(nme);
	}

	protected String parseMessage(NamingException nme) {
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

	private void parse(NamingException nme) {
		message = parseMessage(nme);
		if (!StringUtil.isNullOrEmpty(message)) {
			final String string = "LDAP: error code ";
			int ldpx = message.indexOf(string);
			if (ldpx != -1) {
				String err = message.substring(ldpx + string.length());
				StringTokenizer t = new StringTokenizer(err, " ,:");
				code = Integer.parseInt(t.nextToken());
				t.nextToken();
				reason = stripTrailing(t.nextToken());
				t.nextToken();
				if(t.hasMoreTokens()) {
					ldapError = t.nextToken();
				}
				while (t.hasMoreTokens()) {
					if (t.nextToken().equals("data")) {
						data = t.nextToken();
					}
				}
			}
		}
	}

	protected String stripTrailing(String text) {
		while (text.endsWith(":")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}
	
	public String getMessage() {
		return message;
	}

	public String getLdapError() {
		return ldapError;
	}

	public int getCode() {
		return code;
	}

	public String getReason() {
		return reason;
	}

	public String getData() {
		return data;
	}

}
