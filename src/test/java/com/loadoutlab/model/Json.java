package com.loadoutlab.model;

import java.util.List;
import java.util.Map;

/**
 * Canonical JSON for the Companion contract (docs/COMPANION_CONTRACT.md):
 * insertion-order maps, Double.toString numbers, strict escaping - the
 * same input always serializes to the same bytes, so model-snapshot
 * goldens diff like the engine goldens do. Values must be JSON-safe
 * (String, Number, Boolean, List, Map, null); anything else is a bug in
 * the model builder and fails loudly.
 */
public final class Json
{
	private Json()
	{
	}

	public static String write(Object value)
	{
		StringBuilder sb = new StringBuilder();
		append(sb, value);
		return sb.toString();
	}

	private static void append(StringBuilder sb, Object v)
	{
		if (v == null)
		{
			sb.append("null");
		}
		else if (v instanceof String)
		{
			string(sb, (String) v);
		}
		else if (v instanceof Boolean || v instanceof Integer || v instanceof Long)
		{
			sb.append(v);
		}
		else if (v instanceof Double || v instanceof Float)
		{
			double d = ((Number) v).doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d))
			{
				throw new IllegalArgumentException("non-finite number in model");
			}
			sb.append(d);
		}
		else if (v instanceof Map)
		{
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
			{
				if (!first)
				{
					sb.append(',');
				}
				first = false;
				string(sb, String.valueOf(e.getKey()));
				sb.append(':');
				append(sb, e.getValue());
			}
			sb.append('}');
		}
		else if (v instanceof List)
		{
			sb.append('[');
			boolean first = true;
			for (Object item : (List<?>) v)
			{
				if (!first)
				{
					sb.append(',');
				}
				first = false;
				append(sb, item);
			}
			sb.append(']');
		}
		else
		{
			throw new IllegalArgumentException("not JSON-safe: " + v.getClass().getName());
		}
	}

	private static void string(StringBuilder sb, String s)
	{
		sb.append('"');
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (c < 0x20)
					{
						sb.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						sb.append(c);
					}
			}
		}
		sb.append('"');
	}
}
