package com.pms.trade_capture;

import org.springframework.boot.SpringApplication;

public class TestTradeCaptureApplication {

	public static void main(String[] args) {
		SpringApplication.from(TradeCaptureApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
