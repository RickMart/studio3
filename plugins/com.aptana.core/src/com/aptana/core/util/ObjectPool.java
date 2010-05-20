package com.aptana.core.util;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * A basic object pool that checks expiration and validation on checkout of instances. Instances are not automatically
 * expired and cleaned via a thread, but instead are only checked on checkout. If validation can be costly this may slow
 * down checkout.
 * 
 * @author cwilliams
 * @param <T>
 */
public abstract class ObjectPool<T> implements IObjectPool<T>
{
	private static final int DEFAULT_EXPIRATION = 30000; // 30 seconds

	private long expirationTime;
	private Hashtable<T, Long> locked, unlocked;

	public ObjectPool(int expirationTime)
	{
		// TODO Allow way to force max pool size!
		this.expirationTime = expirationTime;
		locked = new Hashtable<T, Long>();
		unlocked = new Hashtable<T, Long>();
	}

	public ObjectPool()
	{
		this(DEFAULT_EXPIRATION);
	}

	/* (non-Javadoc)
	 * @see com.aptana.core.util.IObjectPool#create()
	 */
	public abstract T create();

	/* (non-Javadoc)
	 * @see com.aptana.core.util.IObjectPool#validate(T)
	 */
	public abstract boolean validate(T o);

	/* (non-Javadoc)
	 * @see com.aptana.core.util.IObjectPool#expire(T)
	 */
	public abstract void expire(T o);

	/* (non-Javadoc)
	 * @see com.aptana.core.util.IObjectPool#checkOut()
	 */
	public synchronized T checkOut()
	{
		long now = System.currentTimeMillis();
		T t;
		if (unlocked.size() > 0)
		{
			Enumeration<T> e = unlocked.keys();
			while (e.hasMoreElements())
			{
				t = e.nextElement();
				// Allow for expiration time of -1, which means never expire!
				if (expirationTime != -1 && (now - unlocked.get(t)) > expirationTime)
				{
					// object has expired
					unlocked.remove(t);
					expire(t);
					t = null;
				}
				else
				{
					if (validate(t))
					{
						unlocked.remove(t);
						locked.put(t, now);
						return t;
					}
					// object failed validation
					unlocked.remove(t);
					expire(t);
					t = null;
				}
			}
		}
		// no objects available, create a new one
		t = create();
		locked.put(t, now);
		return t;
	}

	/* (non-Javadoc)
	 * @see com.aptana.core.util.IObjectPool#checkIn(T)
	 */
	public synchronized void checkIn(T t)
	{
		locked.remove(t);
		unlocked.put(t, System.currentTimeMillis());
	}

	/* (non-Javadoc)
	 * @see com.aptana.core.util.IObjectPool#cleanup()
	 */
	public synchronized void cleanup()
	{
		for (T t : unlocked.keySet())
		{
			expire(t);
		}
		unlocked.clear();
		// TODO Also expire all the locked ones?
	}
}
