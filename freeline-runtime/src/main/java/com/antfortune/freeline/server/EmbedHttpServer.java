package com.antfortune.freeline.server;

import android.text.TextUtils;

import com.antfortune.freeline.router.ISchemaAction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class EmbedHttpServer implements Runnable {
	private int port;
	private ServerSocket serverSocket;

	public EmbedHttpServer(int port) {
		this.port = port;
	}

	public void start() throws IOException {
		if (serverSocket == null) {
			serverSocket = new ServerSocket(port);
			new Thread(this, "embed-http-server").start();
		}
	}

	public void stop() throws IOException {
		if (serverSocket != null) {
			serverSocket.close();
			serverSocket = null;
		}
	}

	protected void handle(String method, String path,
			HashMap<String, String> headers, Map<String, String> queries, InputStream input,
			ResponseOutputStream response) throws Exception {
	}

	@Override
	public void run() {
		final ServerSocket ss = serverSocket;
		while (ss == serverSocket) {
			Socket conn = null;
			try {
				conn = ss.accept();
				String method = null;
				String path = null;
				HashMap<String, String> headers = new HashMap<String, String>();

				InputStream ins = conn.getInputStream();
				StringBuilder sb = new StringBuilder(512);
				int l;
				while ((l = ins.read()) != -1) {
					if (l == '\n') {
						if (sb.length() > 0
								&& sb.charAt(sb.length() - 1) == '\r')
							sb.setLength(sb.length() - 1);
						if (sb.length() == 0) {
							// header end
							break;
						} else if (method == null) {
							int i = sb.indexOf(" ");
							method = sb.substring(0, i);
							int j = sb.lastIndexOf(" HTTP/");
							path = sb.substring(i + 1, j).trim();
						} else {
							int i = sb.indexOf(":");
							String name = sb.substring(0, i).trim();
							String val = sb.substring(i + 1).trim();
							headers.put(name, val);
						}
						sb.setLength(0);
					} else {
						sb.append((char) l);
					}
				}
				int contentLength = 0;
				String str = headers.get("Content-Length");
				if (str != null) {
					contentLength = Integer.parseInt(str);
				}
				OutputStream os = conn.getOutputStream();
				str = headers.get("Expect");
				if ("100-Continue".equalsIgnoreCase(str)) {
					os.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes("ASCII"));
					os.flush();
				}
				BodyInputStream input = new BodyInputStream(ins, contentLength);
				ResponseOutputStream response = new ResponseOutputStream(os);

				Map<String, String> queries = parsePath(path);
				handle(method, path, headers, queries, input, response);
				response.close();

				conn.close();
				conn = null;
			} catch (Exception e) {
				if (conn != null) {
					try {
						conn.close();
					} catch (Exception ee) {
					}
				}
			}

			if (!ss.isBound() || ss.isClosed()) {
				serverSocket = null;
			}
		}
	}

	private static Map<String, String> parsePath(String path) {
		if (TextUtils.isEmpty(path)) {
			return null;
		}

		int descriptionIndex = path.indexOf("/");
		if (descriptionIndex == -1) {
			return null;
		}

		String description = path.substring(descriptionIndex + 1);

		Map<String, String> queries = new HashMap<>();
		int queryIndex = description.indexOf("?");
		if (queryIndex == -1) {
			queries.put(ISchemaAction.DESCRIPTION, description);
		} else {
			queries.put(ISchemaAction.DESCRIPTION, description.substring(0, queryIndex));
			description = description.substring(queryIndex + 1);
		}

		String[] arr = description.split("&");
		for (String segment : arr) {
			String[] query = segment.split("=");
			if (query.length == 2) {
				queries.put(query[0], query[1]);
			} else if (query.length == 1) {
                queries.put(query[0], "");
            }
		}

		return queries;
	}

	private static class BodyInputStream extends InputStream {
		private InputStream ins;
		private int n;

		public BodyInputStream(InputStream ins, int n) {
			this.ins = ins;
			this.n = n;
		}

		@Override
		public int available() throws IOException {
			return n;
		}

		@Override
		public int read() throws IOException {
			if (n <= 0)
				return -1;
			int r = ins.read();
			if (r != -1)
				n--;
			return r;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (n <= 0)
				return -1;
			int l = ins.read(b, off, len < n ? len : n);
			if (l != -1)
				n -= l;
			return l;
		}

		@Override
		public long skip(long n) throws IOException {
			throw new IOException("unsupported");
		}

		@Override
		public void close() throws IOException {
			ins.close();
		}

		@Override
		public synchronized void mark(int readlimit) {
			throw new UnsupportedOperationException();
		}

		@Override
		public synchronized void reset() throws IOException {
			throw new IOException("unsupported");
		}

		@Override
		public boolean markSupported() {
			return false;
		}
	}

	public static class ResponseOutputStream extends OutputStream {
		private static final byte[] CRLF = { (byte) '\r', (byte) '\n' };
		private OutputStream os;
		private int lv; // 0:statusLine, 1:headers, 2:body, 3:closed

		public ResponseOutputStream(OutputStream os) {
			this.os = os;
		}

		public void setStatusCode(int statusCode) throws IOException {
			switch (statusCode) {
			case 200:
				setStatusLine("200 OK");
				break;
			case 201:
				setStatusLine("201 Created");
				break;
			case 202:
				setStatusLine("202 Accepted");
				break;
			case 301:
				setStatusLine("301 Moved Permanently");
				break;
			case 304:
				setStatusLine("304 Not Modified");
				break;
			case 400:
				setStatusLine("400 Bad Request");
				break;
			case 401:
				setStatusLine("401 Unauthorized");
				break;
			case 403:
				setStatusLine("403 Forbidden");
				break;
			case 404:
				setStatusLine("404 Not Found");
				break;
			case 405:
				setStatusLine("405 Method Not Allowed");
				break;
			case 500:
				setStatusLine("500 Internal Server Error");
				break;
			case 501:
				setStatusLine("501 Not Implemented");
				break;
			default:
				setStatusLine(String.valueOf(statusCode));
				break;
			}
		}

		/**
		 * like "200 OK"
		 */
		public void setStatusLine(String statusLine) throws IOException {
			if (lv == 0) {
				os.write("HTTP/1.1 ".getBytes("ASCII"));
				os.write(statusLine.getBytes("ASCII"));
				os.write(CRLF);
				lv = 1;
			} else {
				throw new IOException("status line is already set");
			}
		}

		public void setHeader(String name, String value) throws IOException {
			if (lv < 1) {
				setStatusCode(200);
			}
			if (lv == 1) {
				os.write(name.getBytes("ASCII"));
				os.write(':');
				os.write(' ');
				os.write(value.getBytes("ASCII"));
				os.write(CRLF);
			} else {
				throw new IOException("headers is already set");
			}
		}

		/**
		 * probably set if has body
		 */
		public void setContentLength(int value) throws IOException {
			setHeader("Content-Length", String.valueOf(value));
		}

		/**
		 * like gzip
		 */
		public void setContentEncoding(String value) throws IOException {
			setHeader("Content-Encoding", value);
		}

		public void setContentType(String value) throws IOException {
			setHeader("Content-Type", value);
		}

		/**
		 * Content-Type: text/plain
		 */
		public void setContentTypeText() throws IOException {
			setContentType("text/plain");
		}

		/**
		 * Content-Type: text/plain; charset=utf-8
		 */
		public void setContentTypeTextUtf8() throws IOException {
			setContentType("text/plain; charset=utf-8");
		}

		/**
		 * Content-Type: text/html
		 */
		public void setContentTypeHtml() throws IOException {
			setContentType("text/html");
		}

		/**
		 * Content-Type: text/html; charset=utf-8
		 */
		public void setContentTypeHtmlUtf8() throws IOException {
			setContentType("text/html; charset=utf-8");
		}

		/**
		 * Content-Type: application/octet-stream
		 */
		public void setContentTypeBinary() throws IOException {
			setContentType("application/octet-stream");
		}

		/**
		 * Content-Type: application/json
		 */
		public void setContentTypeJson() throws IOException {
			setContentType("application/json");
		}

		/**
		 * Content-Type: text/xml
		 */
		public void setContentTypeXml() throws IOException {
			setContentType("text/xml");
		}

		/**
		 * Content-Type: application/zip
		 */
		public void setContentTypeZip() throws IOException {
			setContentType("application/zip");
		}

		/**
		 * Content-Type: image/jpeg
		 */
		public void setContentTypeJpeg() throws IOException {
			setContentType("image/jpeg");
		}

		/**
		 * Content-Type: image/png
		 */
		public void setContentTypePng() throws IOException {
			setContentType("image/png");
		}

		@Override
		public void write(int b) throws IOException {
			if (lv < 1) {
				setStatusCode(200);
			}
			if (lv < 2) {
				os.write(CRLF);
				lv = 2;
			}
			os.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (lv < 1) {
				setStatusCode(200);
			}
			if (lv < 2) {
				os.write(CRLF);
				lv = 2;
			}
			os.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			os.flush();
		}

		@Override
		public void close() throws IOException {
			if (lv < 1) {
				setStatusCode(404);
			}
			if (lv < 2) {
				os.write(CRLF);
				lv = 2;
			}
			if (lv < 3) {
				os.close();
				lv = 3;
			}
		}
	}
}
