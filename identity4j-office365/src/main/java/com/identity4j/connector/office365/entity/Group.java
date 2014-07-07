package com.identity4j.connector.office365.entity;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * This class represents a Group Object of the WAAD top level entity Group. Also this class gives a publicly available
 * access mechanism to access each individual member variables such as Object Id, DisplayName etc.
 * @author gaurav
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class Group {
	
	private String objectId;	
	private String description;
	private String dirSyncEnabled;
	private String displayName;
	private String lastDirSyncTime;
	private String mail;
	private String mailEnabled;
	private String securityEnabled;
	private String mailNickname;

	/**
	 * @return The objectId of this Group.
	 */
	public String getObjectId() {
		return objectId;
	}
	/**
	 * @param objectId The objectId to set.
	 */
	public void setObjectId(String objectId) {
		this.objectId = objectId;
	}
	/**
	 * @return The dirSyncEnabled attribute of this Group.
	 */
	public String getDirSyncEnabled() {
		return dirSyncEnabled;
	}
	/**
	 * @param dirSyncEnabled The dirSyncEnabled to set.
	 */
	public void setDirSyncEnabled(String dirSyncEnabled) {
		this.dirSyncEnabled = dirSyncEnabled;
	}
	/**
	 * @return The description of the Group.
	 */
	public String getDescription() {
		return description;
	}
	/**
	 * @param description The description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	/**
	 * @return The displayName of this Group.
	 */
	public String getDisplayName() {
		return displayName;
	}
	/**
	 * @param displayName The displayName to set to this Group.
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	/**
	 * @return The lastDirSyncTime of this Group.
	 */
	public String getLastDirSyncTime() {
		return lastDirSyncTime;
	}
	/**
	 * @param lastDirSyncTime The lastDirSyncTime to set to this Group.
	 */
	public void setLastDirSyncTime(String lastDirSyncTime) {
		this.lastDirSyncTime = lastDirSyncTime;
	}
	/**
	 * @return The mail attribute of this Group.
	 */
	public String getMail() {
		return this.mail;
	}
	/**
	 * @param mail The mail to set to this Group.
	 */
	public void setMail(String mail) {
		this.mail = mail;
	}
	/**
	 * @return The mailEnabled attribute of this Group.
	 */
	public String getMailEnabled() {
		return mailEnabled;
	}
	/**
	 * @param mailEnabled The mailEnabled to set to this Group.
	 */
	public void setMailEnabled(String mailEnabled) {
		this.mailEnabled = mailEnabled;
	}
	/**
	 * @return The securityEnabled attribute of this Group.
	 */
	public String getSecurityEnabled() {
		return securityEnabled;
	}
	/**
	 * @param securityEnabled The securityEnabled to set to this Group.
	 */
	public void setSecurityEnabled(String securityEnabled) {
		this.securityEnabled = securityEnabled;
	}
	public String getMailNickname() {
		return mailNickname;
	}
	public void setMailNickname(String mailNickname) {
		this.mailNickname = mailNickname;
	}
	

}
