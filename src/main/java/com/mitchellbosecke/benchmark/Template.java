
package com.mitchellbosecke.benchmark;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Template {
	private final String template;
	private final List<TemplatePart> parts;

	public Template (String template) {
		this.template = template;
		List<TemplatePart> parsedParts = parseTemplate(template);
		parts = resolve(parsedParts);
	}

	private List<TemplatePart> parseTemplate (String template) {
		List<TemplatePart> result = new ArrayList<TemplatePart>();
		StringBuilder span = new StringBuilder();
		int i = 0;
		while (true) {
			if (i >= template.length()) break;
			int opening = template.indexOf("{{", i);
			if (opening >= 0) {
				span.append(template.substring(i, opening));
				result.add(new Span(span.toString(), i, opening));
				span = new StringBuilder();

				int closing = template.indexOf("}}", i);
				if (closing >= 0) {
					String placeholder = template.substring(opening + 2, closing).trim();
					if (placeholder.contains("{{")) {
						Line line = getLine(template, opening);
						throw new RuntimeException("No closing tag }} for opening tag at line " + line.index + "\n" + line.text);
					}
					if (!placeholder.isEmpty()) result.add(new Temporary(placeholder, opening, closing + 2));
					i = closing + 2;
				} else {
					Line line = getLine(template, i);
					throw new RuntimeException("No closing tag }} for opening tag at line " + line.index + "\n" + line.text);
				}
			} else {
				span.append(template.substring(i, template.length()));
				result.add(new Span(span.toString(), i, template.length()));
				break;
			}
		}
		return result;
	}

	private List<TemplatePart> resolve (List<TemplatePart> parts) {
		List<TemplatePart> resolved = new ArrayList<TemplatePart>();
		resolve(parts.iterator(), resolved);
		return resolved;
	}

	private Expression extractExpression (Temporary temp, String prefix) {
		String value = temp.value;
		if (!value.startsWith(prefix)) throw new RuntimeException("Couldn't extract expression");
		String exprValue = value;
		exprValue = exprValue.substring(prefix.length(), exprValue.length()).trim();
		return new Expression(exprValue, temp.start + exprValue.length(), temp.end);
	}

	private void resolve (Iterator<TemplatePart> parts, List<TemplatePart> resolved) {
		while (parts.hasNext()) {
			TemplatePart part = parts.next();
			resolveStatement(part, parts, resolved);
		}
	}

	private void resolveStatement (TemplatePart part, Iterator<TemplatePart> parts, List<TemplatePart> resolved) {
		if (part instanceof Span) {
			resolved.add(part);
		} else {
			Temporary temp = (Temporary)part;
			String value = temp.value;
			if (value.startsWith("#if ")) {
				resolved.add(resolveIf(temp, parts));
			} else if (value.startsWith("#for ")) {
				resolved.add(resolveFor(temp, parts));
			} else {
				resolved.add(new Expression(value, part.start, part.end));
			}
		}
	}

	private TemplatePart resolveIf (Temporary ifTemp, Iterator<TemplatePart> parts) {
		Expression expr = extractExpression(ifTemp, "#if ");
		ArrayList<TemplatePart> trueBodyParts = new ArrayList<TemplatePart>();
		ArrayList<IfBlock> elseIfs = new ArrayList<IfBlock>();
		IfBlock elseIf = null;
		ArrayList<TemplatePart> falseBodyParts = new ArrayList<TemplatePart>();
		ArrayList<TemplatePart> bodyParts = trueBodyParts;
		TemplatePart end = null;
		boolean hasElse = false;

		while (parts.hasNext()) {
			TemplatePart part = parts.next();

			if (part instanceof Span) {
				bodyParts.add(part);
			} else {
				Temporary temp = (Temporary)part;
				String tempValue = temp.value;
				if (tempValue.equals("#else")) {
					if (elseIf != null) {
						elseIfs.add(elseIf);
						elseIf = null;
					}
					bodyParts = falseBodyParts;
					hasElse = true;
				} else if (tempValue.startsWith("#elseif ")) {
					if (hasElse) throw new RuntimeException("Else block must be after all elseif blocks.");
					if (elseIf != null) elseIfs.add(elseIf);
					elseIf = new IfBlock(extractExpression(temp, "#elseif "), temp.start, temp.end);
					elseIf.trueBlock = new Block(temp.start, temp.end);
					bodyParts = elseIf.trueBlock.statements;
				} else if (tempValue.equals("#end")) {
					if (elseIf != null) {
						elseIfs.add(elseIf);
						elseIf = null;
					}
					end = temp;
					break;
				} else {
					resolveStatement(temp, parts, bodyParts);
				}
			}
		}

		if (end == null) throw new RuntimeException("Not matching #end for #if");

		IfBlock ifBlock = new IfBlock(expr, ifTemp.start, end.end);

		ifBlock.trueBlock = new Block(ifTemp.start, ifTemp.end);
		ifBlock.trueBlock.statements.addAll(trueBodyParts);

		ifBlock.elseifBlocks.addAll(elseIfs);

		ifBlock.falseBlock = new Block(ifTemp.start, ifTemp.end);
		ifBlock.falseBlock.statements.addAll(falseBodyParts);
		return ifBlock;
	}

	private TemplatePart resolveFor (Temporary forTemp, Iterator<TemplatePart> parts) {
		int inIndex = forTemp.value.indexOf(" in ");
		if (inIndex < 0) throw new RuntimeException("For itemName in items expected.");
		String itemName = forTemp.value.substring("#for ".length(), inIndex).trim();
		String items = forTemp.value.substring(inIndex + " in ".length());
		ArrayList<TemplatePart> bodyParts = new ArrayList<TemplatePart>();
		Temporary end = null;

		while (parts.hasNext()) {
			TemplatePart part = parts.next();

			if (part instanceof Span) {
				bodyParts.add(part);
			} else {
				Temporary temp = (Temporary)part;
				if (temp.value.equals("#end")) {
					end = temp;
					break;
				} else {
					resolveStatement(temp, parts, bodyParts);
				}
			}
		}

		if (end == null) throw new RuntimeException("No matchin #end for for #loop.");

		ForBlock forBlock = new ForBlock(itemName, new Expression(items, forTemp.start, forTemp.end), forTemp.start, end.end);
		forBlock.body = new Block(forTemp.start, end.end);
		forBlock.body.statements.addAll(bodyParts);
		return forBlock;
	}

	public String render (TemplateContext ctx) {
		StringBuilder out = new StringBuilder();
		for (TemplatePart part : parts) {
			part.render(ctx, out);
		}
		return out.toString();
	}

	static Line getLine (String str, int index) {
		int count = 0;
		int lineStart = 0;
		for (int i = 0; i < str.length() && i < index; i++) {
			char c = str.charAt(i);
			if (c == '\n') {
				++count;
				lineStart = i;
			}
		}

		int lineEnd = lineStart;
		for (int i = lineStart + 1; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c == '\n') {
				lineEnd = i;
				break;
			}
		}

		return new Line(count + 1, str.substring(lineStart, lineEnd).trim());
	}

	static class Line {
		final int index;
		final String text;

		public Line (int index, String text) {
			super();
			this.index = index;
			this.text = text;
		}
	}

	static abstract class TemplatePart {
		final int start;
		final int end;

		public TemplatePart (int start, int end) {
			super();
			this.start = start;
			this.end = end;
		}

		abstract void render (TemplateContext ctx, StringBuilder out);
	}

	static class Span extends TemplatePart {
		final String span;

		Span (String span, int start, int end) {
			super(start, end);
			this.span = span;
		}

		@Override
		public void render (TemplateContext ctx, StringBuilder out) {
			out.append(span);
		}
	}

	static class Temporary extends TemplatePart {
		final String value;

		Temporary (String value, int start, int end) {
			super(start, end);
			this.value = value;
		}

		@Override
		public void render (TemplateContext ctx, StringBuilder out) {
			throw new IllegalStateException("Temporary part should never be rendered.");
		}
	}

	static class Expression extends TemplatePart {
		final boolean not;
		final String value;
		final String[] parts;

		public Expression (String value, int start, int end) {
			super(start, end);
			if (value.startsWith("!")) {
				not = true;
				value = value.substring(1);
			} else {
				not = false;
			}
			this.value = value;
			this.parts = value.split("\\.");
		}

		@Override
		public void render (TemplateContext ctx, StringBuilder out) {
			Object val = evaluate(ctx);
			out.append(val);
		}

		static Map<Class, Map<String, Field>> fieldCache = new ConcurrentHashMap<Class, Map<String, Field>>();

		Object evaluate (TemplateContext ctx) {
			Object val = ctx.get(value);
			if (val != null) return val;

			val = ctx.get(parts[0]);
			if (val == null)
				throw new RuntimeException("Can't find context entry for " + parts[0]);

			for (int i = 1; i < parts.length; i++) {
				try {
					Class cls = val.getClass();
					Field field = null;

					Map<String, Field> cachedClass = fieldCache.get(cls);
					if (cachedClass != null) {
						field = cachedClass.get(parts[i]);
					}

					if (field == null) {
						field = cls.getDeclaredField(parts[i]);
						field.setAccessible(true);

						if (cachedClass == null) {
							cachedClass = new ConcurrentHashMap<String, Field>();
							fieldCache.put(cls, cachedClass);
						}

						cachedClass.put(parts[i], field);
					}

					val = field.get(val);

				} catch (Throwable e) {
					throw new RuntimeException("Can't access field " + value + " in expression " + value, e);
				}
			}
			return val;
		}

		boolean evaluateBoolean (TemplateContext ctx) {
			Object value = evaluate(ctx);
			if (value == null) return false;
			if (value instanceof Boolean) return not ^ (Boolean)value;
			throw new RuntimeException("Expression must evaluate to a boolean.");
		}
	}

	static class Block extends TemplatePart {
		final ArrayList<TemplatePart> statements = new ArrayList<TemplatePart>();

		public Block (int start, int end) {
			super(start, end);
		}

		@Override
		public void render (TemplateContext ctx, StringBuilder out) {
			for (TemplatePart s : statements)
				s.render(ctx, out);
		}
	}

	static class IfBlock extends TemplatePart {
		Expression expression;
		Block trueBlock;
		List<IfBlock> elseifBlocks = new ArrayList<IfBlock>();
		Block falseBlock;

		public IfBlock (Expression expression, int start, int end) {
			super(start, end);
			this.expression = expression;
		}

		@Override
		public void render (TemplateContext ctx, StringBuilder out) {
			boolean isTrue = expression.evaluateBoolean(ctx);
			if (isTrue) {
				trueBlock.render(ctx, out);
			} else {
				if (elseifBlocks.size() > 0) {
					for (IfBlock elseIf : elseifBlocks) {
						if (elseIf.expression.evaluateBoolean(ctx)) {
							elseIf.trueBlock.render(ctx, out);
							break;
						}
					}
				} else {
					falseBlock.render(ctx, out);
				}
			}
		}
	}

	static class ForBlock extends TemplatePart {
		String itemName;
		Expression items;
		Block body;

		public ForBlock (String itemName, Expression items, int start, int end) {
			super(start, end);
			this.itemName = itemName;
			this.items = items;
		}

		@Override
		public void render (TemplateContext ctx, StringBuilder out) {
			Object iterable = items.evaluate(ctx);
			if (!(iterable instanceof Iterable || iterable.getClass().isArray())) throw new RuntimeException("Source in for loop must be of type Iterable.");
			ctx.push();
			Map<String, Object> scope = ctx.getScope();
			if (!iterable.getClass().isArray()) {
				for (Object item : (Iterable)iterable) {
					scope.put(itemName, item);
					body.render(ctx, out);
				}
			} else {
				if(iterable.getClass() == byte[].class) {
					byte[] array = (byte[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else if(iterable.getClass() == char[].class) {
					char[] array = (char[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else if(iterable.getClass() == short[].class) {
					short[] array = (short[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else if(iterable.getClass() == int[].class) {
					int[] array = (int[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else if(iterable.getClass() == long[].class) {
					long[] array = (long[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else if(iterable.getClass() == float[].class) {
					float[] array = (float[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else if(iterable.getClass() == double[].class) {
					double[] array = (double[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else if(iterable.getClass() == boolean[].class) {
					boolean[] array = (boolean[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				} else {
					Object[] array = (Object[])iterable;
					for (int i = 0; i < array.length; i++) {
						scope.put(itemName, array[i]);
						body.render(ctx, out);
					}
				}
			}
			ctx.pop();
		}
	}

	public static class TemplateContext {
		final List<Map<String, Object>> symbols = new ArrayList<Map<String, Object>>();

		public TemplateContext () {
			push();
		}

		public void set (String name, Object value) {
			symbols.get(symbols.size() - 1).put(name, value);
		}

		Object get (String name) {
			for (int i = symbols.size() - 1; i >= 0; i--) {
				Map<String, Object> ctx = symbols.get(i);
				Object value = ctx.get(name);
				if (value != null) return value;
			}
			return null;
		}

		void push () {
			symbols.add(new HashMap<String, Object>(16));
		}

		Map<String, Object> getScope () {
			return symbols.get(symbols.size() - 1);
		}

		void pop () {
			symbols.remove(symbols.size() - 1);
		}
	}
}
