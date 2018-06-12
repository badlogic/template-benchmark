package com.mitchellbosecke.benchmark;

import java.io.IOException;
import java.util.List;

import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import com.mitchellbosecke.benchmark.model.Stock;


public class JTwig extends BaseBenchmark {
	JtwigTemplate template;
	List<Stock> items;

	@Setup
	public void setup() throws IOException {
		template = JtwigTemplate.classpathTemplate("/templates/stocks.jtwig.html");
		items = Stock.dummyItems();
	}

	@Benchmark
	public String benchmark() {
		JtwigModel model = JtwigModel.newModel().with("items", items);
		return template.render(model);
	}
}
