package com.mitchellbosecke.benchmark;

import java.io.IOException;
import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import com.mitchellbosecke.benchmark.model.Stock;

import io.marioslab.basis.template.TemplateContext;
import io.marioslab.basis.template.TemplateLoader;
import io.marioslab.basis.template.TemplateLoader.ClasspathTemplateLoader;

public class BasisTemplate extends BaseBenchmark {
	io.marioslab.basis.template.Template template;
	List<Stock> items;

	@Setup
	public void setup() throws IOException {
		TemplateLoader loader = new ClasspathTemplateLoader();

		template = loader.load("/templates/stocks.basis.html");
		items = Stock.dummyItems();
	}

	@Benchmark
	public String benchmark() {
		TemplateContext ctx = new TemplateContext();
		ctx.set("items", items);
		return template.render(ctx);
	}

	public static void main(String[] args) throws IOException {
		BasisTemplate b = new BasisTemplate();
		b.setup();
		while (true)
			b.benchmark();
	}
}
