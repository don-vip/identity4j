package com.identity4j.util.crypt.nss;

/*
 * #%L
 * Identity4J Utils
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

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class DefaultNssTokenDatabase extends NssTokenDatabase {

    public DefaultNssTokenDatabase() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException,
        InterruptedException {
        super();
    }

    public DefaultNssTokenDatabase(byte[] noise, byte[] passphrase) {
        super(noise, passphrase);
    }

    public DefaultNssTokenDatabase(File dbDir, byte[] noise, byte[] passphrase) {
        super(dbDir, noise, passphrase);
    }

    public DefaultNssTokenDatabase(File dbDir) {
        super(dbDir);
    }

}
