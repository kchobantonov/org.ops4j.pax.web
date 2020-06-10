/*
 * Copyright 2020 OPS4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.web.service.undertow.internal;

import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.ServletContext;

import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import org.ops4j.pax.web.service.spi.model.elements.FilterModel;
import org.ops4j.pax.web.service.spi.servlet.OsgiInitializedFilter;
import org.ops4j.pax.web.service.spi.servlet.OsgiScopedServletContext;
import org.ops4j.pax.web.service.spi.servlet.OsgiServletContext;
import org.ops4j.pax.web.service.spi.servlet.ScopedFilter;
import org.osgi.framework.ServiceReference;

/**
 * Special {@link FilterInfo} that can be configured from {@link FilterModel}.
 */
public class PaxWebFilterInfo extends FilterInfo {

	private final FilterModel filterModel;

	/** This {@link ServletContext} is scoped to single {@link org.osgi.service.http.context.ServletContextHelper} */
	private final OsgiServletContext osgiServletContext;
	/** This {@link ServletContext} is scoped to particular Whiteboard filter - but only at init() time. */
	private final OsgiScopedServletContext servletContext;

	private ServiceReference<? extends Filter> serviceReference;

	public PaxWebFilterInfo(FilterModel model, OsgiServletContext osgiServletContext) {
		super(model.getName(), model.getActualClass(),
				new FilterModelFactory(model,
						new OsgiScopedServletContext(osgiServletContext, model.getRegisteringBundle())));

		this.osgiServletContext = osgiServletContext;

		this.filterModel = model;

		for (Map.Entry<String, String> param : filterModel.getInitParams().entrySet()) {
			addInitParam(param.getKey(), param.getValue());
		}
		setAsyncSupported(filterModel.getAsyncSupported() != null && filterModel.getAsyncSupported());

		filterModel.getInitParams().forEach(this::addInitParam);

		this.servletContext = ((FilterModelFactory)super.getInstanceFactory()).getServletContext();
	}


	@Override
	@SuppressWarnings("MethodDoesntCallSuperMethod")
	public FilterInfo clone() {
		final FilterInfo info = new PaxWebFilterInfo(this.filterModel, this.osgiServletContext);

		info.setAsyncSupported(isAsyncSupported());
		getInitParams().forEach(info::addInitParam);

		return info;
	}

	public FilterModel getFilterModel() {
		return filterModel;
	}

	/**
	 * An {@link InstanceFactory} that returns {@link Filter filter instance} from {@link FilterModel}.
	 */
	private static class FilterModelFactory implements InstanceFactory<Filter> {

		private final FilterModel model;
		private final OsgiScopedServletContext osgiScopedServletContext;

		FilterModelFactory(FilterModel model, OsgiScopedServletContext osgiScopedServletContext) {
			this.model = model;
			this.osgiScopedServletContext = osgiScopedServletContext;
		}

		@Override
		public InstanceHandle<Filter> createInstance() throws InstantiationException {
			Filter instance = model.getFilter();
			if (instance == null) {
				if (model.getElementReference() != null) {
					// obtain Filter using reference
					instance = model.getRegisteringBundle().getBundleContext().getService(model.getElementReference());
					if (instance == null) {
						throw new RuntimeException("Can't get a Filter service from the reference " + model.getElementReference());
					}
				} else if (model.getFilterClass() != null) {
					try {
						instance = model.getFilterClass().newInstance();
					} catch (Exception e) {
						InstantiationException instantiationException = new InstantiationException(e.getMessage());
						instantiationException.initCause(e);
						throw instantiationException;
					}
				}
			}

			Filter osgiInitializedFilter = new OsgiInitializedFilter(instance, this.osgiScopedServletContext);
			Filter scopedFilter = new ScopedFilter(osgiInitializedFilter, model);

			return new ImmediateInstanceHandle<Filter>(scopedFilter);
		}

		public OsgiScopedServletContext getServletContext() {
			return osgiScopedServletContext;
		}
	}

}
