/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.ObjectUtils;

/**
 * {@link ConfigurationPropertySource} backed by a {@link EnumerablePropertySource}.
 * Extends {@link SpringConfigurationPropertySource} with full "relaxed" mapping support.
 * In order to use this adapter the underlying {@link PropertySource} must be fully
 * enumerable. A security restricted {@link SystemEnvironmentPropertySource} cannot be
 * adapted.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @see PropertyMapper
 */
class SpringIterableConfigurationPropertySource extends SpringConfigurationPropertySource
		implements IterableConfigurationPropertySource {

	private volatile Object cacheKey;

	private volatile Cache cache;

	SpringIterableConfigurationPropertySource(EnumerablePropertySource<?> propertySource,
			PropertyMapper mapper) {
		super(propertySource, mapper, null);
		assertEnumerablePropertySource(propertySource);
	}

	private void assertEnumerablePropertySource(
			EnumerablePropertySource<?> propertySource) {
		if (getPropertySource() instanceof MapPropertySource) {
			try {
				((MapPropertySource) getPropertySource()).getSource().size();
			}
			catch (UnsupportedOperationException ex) {
				throw new IllegalArgumentException(
						"PropertySource must be fully enumerable");
			}
		}
	}

	@Override
	public ConfigurationProperty getConfigurationProperty(
			ConfigurationPropertyName name) {
		ConfigurationProperty configurationProperty = super.getConfigurationProperty(
				name);
		if (configurationProperty == null) {
			configurationProperty = find(getPropertyMappings(getCache()), name);
		}
		return configurationProperty;
	}

	@Override
	public Stream<ConfigurationPropertyName> stream() {
		return getConfigurationPropertyNames().stream();
	}

	@Override
	public Iterator<ConfigurationPropertyName> iterator() {
		return getConfigurationPropertyNames().iterator();
	}

	@Override
	public ConfigurationPropertyState containsDescendantOf(
			ConfigurationPropertyName name) {
		return ConfigurationPropertyState.search(this, name::isAncestorOf);
	}

	private List<ConfigurationPropertyName> getConfigurationPropertyNames() {
		Cache cache = getCache();
		List<ConfigurationPropertyName> names = (cache != null ? cache.getNames() : null);
		if (names != null) {
			return names;
		}
		PropertyMapping[] mappings = getPropertyMappings(cache);
		names = new ArrayList<>(mappings.length);
		for (PropertyMapping mapping : mappings) {
			names.add(mapping.getConfigurationPropertyName());
		}
		names = Collections.unmodifiableList(names);
		if (cache != null) {
			cache.setNames(names);
		}
		return names;
	}

	private PropertyMapping[] getPropertyMappings(Cache cache) {
		PropertyMapping[] result = (cache != null ? cache.getMappings() : null);
		if (result != null) {
			return result;
		}
		String[] names = getPropertySource().getPropertyNames();
		List<PropertyMapping> mappings = new ArrayList<>(names.length * 2);
		for (String name : names) {
			for (PropertyMapping mapping : getMapper().map(name)) {
				mappings.add(mapping);
			}
		}
		result = mappings.toArray(new PropertyMapping[mappings.size()]);
		if (cache != null) {
			cache.setMappings(result);
		}
		return result;
	}

	private Cache getCache() {
		Object cacheKey = getCacheKey();
		if (cacheKey == null) {
			return null;
		}
		if (ObjectUtils.nullSafeEquals(cacheKey, this.cacheKey)) {
			return this.cache;
		}
		this.cache = new Cache();
		this.cacheKey = cacheKey;
		return this.cache;
	}

	private Object getCacheKey() {
		if (getPropertySource() instanceof MapPropertySource) {
			return ((MapPropertySource) getPropertySource()).getSource().keySet();
		}
		return getPropertySource().getPropertyNames();
	}

	@Override
	protected EnumerablePropertySource<?> getPropertySource() {
		return (EnumerablePropertySource<?>) super.getPropertySource();
	}

	private static class Cache {

		private List<ConfigurationPropertyName> names;

		private PropertyMapping[] mappings;

		public List<ConfigurationPropertyName> getNames() {
			return this.names;
		}

		public void setNames(List<ConfigurationPropertyName> names) {
			this.names = names;
		}

		public PropertyMapping[] getMappings() {
			return this.mappings;
		}

		public void setMappings(PropertyMapping[] mappings) {
			this.mappings = mappings;
		}

	}

}