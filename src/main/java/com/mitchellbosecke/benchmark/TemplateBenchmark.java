package com.mitchellbosecke.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

import com.mitchellbosecke.benchmark.model.Stock;

public class TemplateBenchmark extends BaseBenchmark {
	Template template;
	List<StockView> views;

	@Setup
	public void setup () throws IOException {
		StringBuilder builder = new StringBuilder();
      try (BufferedReader in = new BufferedReader(new InputStreamReader(TemplateBenchmark.class.getResourceAsStream("/templates/stocks.template.html")))) {
          for (;;) {
              String line = in.readLine();
              if (line == null) {
                break;
              }
              builder.append(line);
          }
      }

		template = new Template(builder.toString());

		views = new ArrayList<StockView>();
		List<Stock> original = (List<Stock>)getContext().get("items");
		int idx = 1;
		for (Stock s: original) {
			views.add(new StockView(idx, idx==1, idx==original.size(), s));
			idx++;
		}
	}

	@Benchmark
	public String benchmark() {
		Template.TemplateContext ctx = new Template.TemplateContext();
	   ctx.set("items", views);
	   return template.render(ctx);
	}

	class StockView {

      public final int index;

      public final boolean first;

      public final boolean last;

      public final String negativeClass;

      public final String rowClass;

      private String name;

      private String name2;

      private String url;

      private String symbol;

      private double price;

      private double change;

      private double ratio;

      public StockView(int index, boolean first, boolean last, Stock value) {
          this.index = index;
          this.first = first;
          this.last = last;
          this.negativeClass = value.getChange() > 0 ? "" : "class=\"minus\"";
          this.rowClass = index % 2 == 0 ? "even" : "odd";
          this.name = value.getName();
          this.name2 = value.getName2();
          this.url = value.getUrl();
          this.symbol = value.getSymbol();
          this.price = value.getPrice();
          this.change = value.getChange();
          this.ratio = value.getRatio();
      }
  }

	public static void main (String[] args) throws IOException {
		TemplateBenchmark b = new TemplateBenchmark();
		b.setup();
		while(true) {
			b.benchmark();
		}
	}
}
