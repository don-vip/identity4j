package com.identity4j.util;

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

public class Color implements RGB {

	private static final long serialVersionUID = 6211754997254209905L;
	
	public final static Color WHITE = new Color(255, 255, 255);
	public final static Color BLACK = new Color(0, 0, 0);
	public final static Color DARK_GREEN = new Color(0, 128, 0);
	public final static Color RED = new Color(255, 0, 0);
	public final static Color DARK_RED = new Color(128, 0, 0);

	private int red;
	private int green;
	private int blue;

	public Color(int red, int green, int blue) {
		super();
		this.red = red;
		this.green = green;
		this.blue = blue;
	}

	public Color(String hex) {
		if (hex == null || hex.trim().equals("")) {
			red = 0;
			green = 0;
			blue = 0;
		} else {
			while (hex.startsWith("#")) {
				hex = hex.substring(1);
			}
			if (hex.length() == 3) {
				red = Integer.parseInt(hex.substring(0, 1), 16) * 16;
				green = Integer.parseInt(hex.substring(1, 2), 16) * 16;
				blue = Integer.parseInt(hex.substring(2, 3), 16) * 16;
			} else if (hex.length() == 6) {
				red = Integer.parseInt(hex.substring(0, 2), 16);
				green = Integer.parseInt(hex.substring(2, 4), 16);
				blue = Integer.parseInt(hex.substring(4, 6), 16);
			} else {
				throw new IllegalArgumentException("'"  + hex + "' not a hex colour");
			}
		}
	}

	public int getBlue() {
		return blue;
	}

	public int getGreen() {
		return green;
	}

	public int getRed() {
		return red;
	}

	public static String toHexString(RGB color) {
		if (color == null) {
			return "auto";
		}
		return "#" + toHexNumber(color);
	}

	public static String toHexNumber(RGB color) {
		return toHexDigits(color.getRed()) + toHexDigits(color.getGreen()) + toHexDigits(color.getBlue());
	}

	public static String toHexDigits(int value) {
		return String.format("%02X", value);
	}
}
