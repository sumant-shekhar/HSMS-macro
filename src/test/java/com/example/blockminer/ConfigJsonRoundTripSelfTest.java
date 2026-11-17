package com.example.blockminer;

import java.lang.reflect.Method;

public final class ConfigJsonRoundTripSelfTest {
	private ConfigJsonRoundTripSelfTest() {
	}

	public static void main(String[] args) throws ReflectiveOperationException {
		Class<?> config = Class.forName("com.example.blockminer.Config");
		Method selfTest = config.getMethod("jsonRoundTripSelfTest");
		if (!Boolean.TRUE.equals(selfTest.invoke(null))) {
			throw new AssertionError("Config JSON round trip changed one or more settings.");
		}
		System.out.println("Config JSON round trip passed.");
	}
}
