
package com.mitchellbosecke.benchmark;

import com.mitchellbosecke.benchmark.model.Stock;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Pebble extends BaseBenchmark {

	private PebbleTemplate template;

	private List<Stock> items;

	@Setup
	public void setup () throws PebbleException {
		PebbleEngine engine = new PebbleEngine.Builder().autoEscaping(false).build();
		template = engine.getTemplate("templates/stocks.pebble.html");
		items = Stock.dummyItems();
	}

	@Benchmark
	public String benchmark () throws PebbleException, IOException {
		StringWriter writer = new StringWriter();
		Map<String, Object> context = new HashMap<String, Object>();
		context.put("items", items);
		template.evaluate(writer, context);
		return writer.toString();
	}

	public static void main (String[] args) throws IOException, PebbleException {
		Pebble b = new Pebble();
		b.setup();
		while (true)
			b.benchmark();
	}
}
