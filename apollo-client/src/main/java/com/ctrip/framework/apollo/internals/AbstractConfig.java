package com.ctrip.framework.apollo.internals;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.dianping.cat.Cat;
import com.dianping.cat.message.Message;
import com.dianping.cat.message.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfig implements Config {
  private static final Logger logger = LoggerFactory.getLogger(AbstractConfig.class);
  private static ExecutorService m_executorService;
  private List<ConfigChangeListener> m_listeners = Lists.newCopyOnWriteArrayList();

  static {
    m_executorService = Executors.newCachedThreadPool(ApolloThreadFactory
        .create("Config", true));

  }

  @Override
  public void addChangeListener(ConfigChangeListener listener) {
    if (!m_listeners.contains(listener)) {
      m_listeners.add(listener);
    }
  }

  @Override
  public Integer getIntProperty(String key, Integer defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  @Override
  public Long getLongProperty(String key, Long defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  @Override
  public Short getShortProperty(String key, Short defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : Short.parseShort(value);
  }

  @Override
  public Float getFloatProperty(String key, Float defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : Float.parseFloat(value);
  }

  @Override
  public Double getDoubleProperty(String key, Double defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : Double.parseDouble(value);
  }

  @Override
  public Byte getByteProperty(String key, Byte defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : Byte.parseByte(value);
  }

  @Override
  public Boolean getBooleanProperty(String key, Boolean defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : Boolean.parseBoolean(value);
  }

  @Override
  public String[] getArrayProperty(String key, String delimiter, String[] defaultValue) {
    String value = getProperty(key, null);
    return value == null ? defaultValue : value.split(delimiter);
  }

  protected void fireConfigChange(final ConfigChangeEvent changeEvent) {
    for (final ConfigChangeListener listener : m_listeners) {
      m_executorService.submit(new Runnable() {
        @Override
        public void run() {
          String listenerName = listener.getClass().getName();
          Transaction transaction = Cat.newTransaction("Apollo.ConfigChangeListener", listenerName);
          try {
            listener.onChange(changeEvent);
            transaction.setStatus(Message.SUCCESS);
          } catch (Throwable ex) {
            transaction.setStatus(ex);
            Cat.logError(ex);
            logger.error("Failed to invoke config change listener {}", listenerName, ex);
          } finally {
            transaction.complete();
          }
        }
      });
    }
  }

  List<ConfigChange> calcPropertyChanges(String namespace, Properties previous,
                                         Properties current) {
    if (previous == null) {
      previous = new Properties();
    }

    if (current == null) {
      current = new Properties();
    }

    Set<String> previousKeys = previous.stringPropertyNames();
    Set<String> currentKeys = current.stringPropertyNames();

    Set<String> commonKeys = Sets.intersection(previousKeys, currentKeys);
    Set<String> newKeys = Sets.difference(currentKeys, commonKeys);
    Set<String> removedKeys = Sets.difference(previousKeys, commonKeys);

    List<ConfigChange> changes = Lists.newArrayList();

    for (String newKey : newKeys) {
      changes.add(new ConfigChange(namespace, newKey, null, current.getProperty(newKey),
          PropertyChangeType.ADDED));
    }

    for (String removedKey : removedKeys) {
      changes.add(new ConfigChange(namespace, removedKey, previous.getProperty(removedKey), null,
          PropertyChangeType.DELETED));
    }

    for (String commonKey : commonKeys) {
      String previousValue = previous.getProperty(commonKey);
      String currentValue = current.getProperty(commonKey);
      if (Objects.equal(previousValue, currentValue)) {
        continue;
      }
      changes.add(new ConfigChange(namespace, commonKey, previousValue, currentValue,
          PropertyChangeType.MODIFIED));
    }

    return changes;
  }
}
