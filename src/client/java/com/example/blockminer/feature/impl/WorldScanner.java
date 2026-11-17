package com.example.blockminer.feature.impl;

import com.example.blockminer.feature.AbstractFeature;

public final class WorldScanner extends AbstractFeature {
	private static final WorldScanner INSTANCE = new WorldScanner();

	private WorldScanner() {
	}

	public static WorldScanner getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "WorldScanner";
	}
}
