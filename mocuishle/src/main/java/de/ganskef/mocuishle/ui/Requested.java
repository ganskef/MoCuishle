package de.ganskef.mocuishle.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import de.ganskef.mocuishle.ui.Requested.RequestedHost;

public class Requested implements Iterable<RequestedHost> {

	private HashMap<String, RequestedHost> mRequestedHosts;

	public Requested() {
		mRequestedHosts = new HashMap<String, RequestedHost>();
	}

	public static class RequestedHost implements Comparable<RequestedHost> {

		private final String mName;

		private final long mTime;

		public RequestedHost(String name) {
			this(name, System.currentTimeMillis());
		}

		private RequestedHost(String name, long time) {
			super();
			this.mName = name;
			this.mTime = time;
		}

		public String getName() {
			return mName;
		}

		public long getTime() {
			return mTime;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((mName == null) ? 0 : mName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			RequestedHost other = (RequestedHost) obj;
			if (mName == null) {
				if (other.mName != null) {
					return false;
				}
			} else if (!mName.equals(other.mName)) {
				return false;
			}
			return true;
		}

		public int compareTo(RequestedHost o) {
			long thisVal = mTime;
			long anotherVal = o.mTime;
			return -(thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
		}

		public long getAge() {
			return (System.currentTimeMillis() - mTime) / 1000;
		}

		public String getUrl() {
			return "http://" + mName + "/";
		}

		public RequestedHost refresh(long offset) {
			return new RequestedHost(mName, System.currentTimeMillis() + offset);
		}
	}

	public void add(String hostName) {
		mRequestedHosts.put(hostName, new RequestedHost(hostName));
	}

	public boolean isEmpty() {
		return mRequestedHosts.isEmpty();
	}

	public Iterator<RequestedHost> iterator() {
		long from = System.currentTimeMillis() - 60000;
		Collection<RequestedHost> hosts = mRequestedHosts.values();
		Iterator<RequestedHost> it = hosts.iterator();
		while (it.hasNext()) {
			RequestedHost each = it.next();
			if (each.getTime() < from) {
				it.remove();
			}
		}
		List<RequestedHost> results = new ArrayList<RequestedHost>(hosts);
		Collections.sort(results);
		return results.iterator();
	}

	public void refresh(String address) {
		Iterator<RequestedHost> it = iterator();
		if (it.hasNext()) {
			RequestedHost first = it.next();
			long offset = first.getAge();
			refresh(first.refresh(offset));
			while (it.hasNext()) {
				RequestedHost each = it.next();
				refresh(each.refresh(offset));
			}
		} else {
			add(address);
		}
	}

	private synchronized void refresh(RequestedHost each) {
		mRequestedHosts.put(each.getName(), each);
	}
}
