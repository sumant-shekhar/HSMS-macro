package com.example.blockminer.feature.impl;

import com.example.blockminer.feature.AbstractFeature;

public final class AutoSell extends AbstractFeature {
	private static final AutoSell INSTANCE = new AutoSell();

	private AutoSell() {
	}

	public static AutoSell getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "AutoSell";
	}
}
