/*********************************************************************************************
 *
 *
 * 'SpeciesDescription.java', in plugin 'msi.gama.core', is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2014 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://code.google.com/p/gama-platform/ for license information and developers contact.
 *
 *
 **********************************************************************************************/
package msi.gaml.descriptions;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;

import gnu.trove.set.hash.TLinkedHashSet;
import msi.gama.common.GamaPreferences;
import msi.gama.common.interfaces.IGamlIssue;
import msi.gama.common.interfaces.ISkill;
import msi.gama.common.interfaces.IVarAndActionSupport;
import msi.gama.metamodel.agent.GamlAgent;
import msi.gama.metamodel.agent.IAgent;
import msi.gama.metamodel.agent.IMacroAgent;
import msi.gama.metamodel.agent.MinimalAgent;
import msi.gama.metamodel.topology.grid.GamaSpatialMatrix.GridPopulation.GamlGridAgent;
import msi.gama.metamodel.topology.grid.GamaSpatialMatrix.GridPopulation.MinimalGridAgent;
import msi.gama.precompiler.GamlProperties;
import msi.gama.precompiler.ITypeProvider;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.GAML;
import msi.gama.util.TOrderedHashMap;
import msi.gaml.architecture.IArchitecture;
import msi.gaml.architecture.reflex.AbstractArchitecture;
import msi.gaml.compilation.AbstractGamlAdditions;
import msi.gaml.compilation.GamaHelper;
import msi.gaml.compilation.IAgentConstructor;
import msi.gaml.descriptions.SymbolSerializer.SpeciesSerializer;
import msi.gaml.expressions.DenotedActionExpression;
import msi.gaml.expressions.IExpression;
import msi.gaml.expressions.ListExpression;
import msi.gaml.expressions.SkillConstantExpression;
import msi.gaml.expressions.SpeciesConstantExpression;
import msi.gaml.factories.ChildrenProvider;
import msi.gaml.factories.DescriptionFactory;
import msi.gaml.statements.Facets;
import msi.gaml.types.GamaType;
import msi.gaml.types.IType;

public class SpeciesDescription extends TypeDescription {
	// AD 08/16: Behaviors are now inherited dynamically
	private TOrderedHashMap<String, StatementDescription> behaviors;
	// AD 08/16: Aspects are now inherited dynamically
	private TOrderedHashMap<String, StatementDescription> aspects;
	private TOrderedHashMap<String, SpeciesDescription> microSpecies;
	protected Map<Class<? extends ISkill>, ISkill> skills;
	protected IArchitecture control;
	private IAgentConstructor agentConstructor;
	private SpeciesConstantExpression speciesExpr;
	protected Class javaBase;
	protected boolean canUseMinimalAgents = true;

	public SpeciesDescription(final String keyword, final SpeciesDescription macroDesc, final ChildrenProvider cp,
			final EObject source, final Facets facets) {
		this(keyword, null, macroDesc, null, cp, source, facets);
	}

	public SpeciesDescription(final String keyword, final Class clazz, final SpeciesDescription macroDesc,
			final SpeciesDescription parent, final ChildrenProvider cp, final EObject source, final Facets facets) {
		super(keyword, clazz, macroDesc, parent, cp, source, facets, null);
		setJavaBase(clazz);
		setSkills(getFacet(SKILLS), Collections.EMPTY_SET);
	}

	/**
	 * This constructor is only called to build built-in species. The parent is
	 * passed directly as there is no ModelFactory to provide it
	 */
	public SpeciesDescription(final String name, final Class clazz, final SpeciesDescription superDesc,
			final SpeciesDescription parent, final IAgentConstructor helper, final Set<String> skills2, final Facets ff,
			final String plugin) {
		super(SPECIES, clazz, superDesc, null, ChildrenProvider.NONE, null, new Facets(NAME, name), plugin);
		setJavaBase(clazz);
		setParent(parent);
		setSkills(ff == null ? null : ff.get(SKILLS), skills2);
		setAgentConstructor(helper);
	}

	@Override
	public SymbolSerializer createSerializer() {
		return SpeciesSerializer.getInstance();
	}

	@Override
	public void dispose() {
		if (isBuiltIn()) {
			return;
		}
		if (behaviors != null) {
			behaviors.clear();
			behaviors = null;
		}
		if (aspects != null) {
			aspects.clear();
			aspects = null;
		}
		if (skills != null) {
			skills.clear();
			skills = null;
		}
		if (control != null) {
			control.dispose();
			control = null;
		}

		if (microSpecies != null) {
			microSpecies.clear();
			microSpecies = null;
		}
		super.dispose();
	}

	protected void setSkills(final IExpressionDescription userDefinedSkills, final Set<String> builtInSkills) {
		final Set<ISkill> skillInstances = new TLinkedHashSet();
		/* We try to add the control architecture if any is defined */
		final IExpressionDescription c = getFacet(CONTROL);
		if (c != null) {
			c.compile(this);
			final Object temp = c.getExpression().value(null);
			if (!(temp instanceof AbstractArchitecture)) {
				warning("This control  does not belong to the list of known agent controls ("
						+ AbstractGamlAdditions.ARCHITECTURES + ")", IGamlIssue.WRONG_CONTEXT, CONTROL);
			} else {
				control = (IArchitecture) temp;
				// We add it explicitly so as to add the variables and actions
				// defined in the control. No need to add it in the other cases
				skillInstances.add(control);
			}
		}

		/* We add the keyword as a possible skill (used for 'grid' species) */
		final ISkill skill = AbstractGamlAdditions.getSkillInstanceFor(getKeyword());
		if (skill != null) {
			skillInstances.add(skill);
		}
		/*
		 * We add the user defined skills (i.e. as in 'species a skills: [s1,
		 * s2...]')
		 */
		if (userDefinedSkills != null) {
			final IExpression expr = userDefinedSkills.compile(this);
			if (expr instanceof ListExpression) {
				final ListExpression list = (ListExpression) expr;
				for (final IExpression exp : list.getElements()) {
					if (exp instanceof SkillConstantExpression) {
						skillInstances.add((ISkill) exp.value(null));
					}
				}
			}
		}
		/*
		 * We add the skills that are defined in Java, either
		 * using @species(value='a', skills= {s1,s2}), or @skill(value="s1",
		 * attach_to="a")
		 */
		for (final String s : builtInSkills) {
			final ISkill sk = AbstractGamlAdditions.getSkillInstanceFor(s);
			if (sk != null) {
				skillInstances.add(sk);
			}
		}

		/* We then create the list of classes from this list of names */
		for (final ISkill skillInstance : skillInstances) {
			if (skillInstance == null) {
				continue;
			}
			final Class<? extends ISkill> skillClass = skillInstance.getClass();
			addSkill(skillClass, skillInstance);
		}

	}

	public String getControlName() {
		String controlName = getLitteral(CONTROL);
		// if the "control" is not explicitly declared then inherit it from the
		// parent species. Takes care of invalid species (see Issue 711)
		if (controlName == null) {
			if (parent != null && parent != this) {
				controlName = getParent().getControlName();
			} else {
				controlName = REFLEX;
			}
		}
		return controlName;
	}

	public ISkill getSkillFor(final Class clazz) {
		ISkill skill = null;
		if (skills != null) {
			skill = skills.get(clazz);
			if (skill == null && clazz != null) {
				for (final Map.Entry<Class<? extends ISkill>, ISkill> entry : skills.entrySet()) {
					if (clazz.isAssignableFrom(entry.getKey())) {
						return entry.getValue();
					}
				}
			}
		}
		// We go and try to find the skill in the parent
		if (skill == null && parent != null && parent != this) {
			return getParent().getSkillFor(clazz);
		}
		return skill;
	}

	public String getParentName() {
		return getLitteral(PARENT);
	}

	@Override
	public IExpression getVarExpr(final String n, final boolean asField) {
		IExpression result = super.getVarExpr(n, asField);
		if (result == null) {
			IDescription desc = getBehavior(n);
			if (desc != null) {
				result = new DenotedActionExpression(desc);
			}
			desc = getAspect(n);
			if (desc != null) {
				result = new DenotedActionExpression(desc);
			}
		}
		return result;
	}

	public void copyJavaAdditions() {
		final Class clazz = getJavaBase();
		if (clazz == null) {
			error("This species cannot be compiled as its Java base is unknown. ", IGamlIssue.UNKNOWN_SUBSPECIES);
			return;
		}
		final Set<IDescription> children = AbstractGamlAdditions.getAllChildrenOf(getJavaBase(), getSkillClasses());
		for (final IDescription v : children) {
			if (v instanceof VariableDescription) {
				boolean toAdd = false;
				if (this.isBuiltIn() || ((VariableDescription) v).isContextualType()) {
					toAdd = true;
				} else {
					if (parent != null && parent != this) {
						final VariableDescription existing = parent.getAttribute(v.getName());
						if (existing == null || !existing.getOriginName().equals(v.getOriginName())) {
							toAdd = true;
						}
					} else
						toAdd = true;
				}
				if (toAdd)
					addChild(v.copy(this));

			} else {
				boolean toAdd = false;
				if (this.isBuiltIn() || parent == null)
					toAdd = true;
				else if (parent != null && parent != this) {
					final StatementDescription existing = parent.getAction(v.getName());
					if (existing == null || !existing.getOriginName().equals(v.getOriginName())) {
						toAdd = true;
					}
				}
				if (toAdd) {
					addChild(v);
				}
			}
		}
	}

	@Override
	public IDescription addChild(final IDescription child) {
		final IDescription desc = super.addChild(child);
		if (desc == null) {
			return null;
		}
		if (desc instanceof StatementDescription) {
			final StatementDescription statement = (StatementDescription) desc;
			final String kw = desc.getKeyword();
			if (PRIMITIVE.equals(kw) || ACTION.equals(kw)) {
				addAction(statement);
			} else if (ASPECT.equals(kw)) {
				addAspect(statement);
			} else {
				addBehavior(statement);
			}
		} else if (desc instanceof VariableDescription) {
			addOwnAttribute((VariableDescription) desc);
		} else if (desc instanceof SpeciesDescription) {
			addMicroSpecies((SpeciesDescription) desc);
		}
		return desc;
	}

	protected void addMicroSpecies(final SpeciesDescription sd) {
		if (!isModel() && sd.isGrid()) {
			sd.error("For the moment, grids cannot be defined as micro-species anywhere else than in the model");
		}
		getMicroSpecies().put(sd.getName(), sd);
		invalidateMinimalAgents();
	}

	protected void invalidateMinimalAgents() {
		canUseMinimalAgents = false;
		if (parent != null && parent != this && !parent.isBuiltIn()) {
			getParent().invalidateMinimalAgents();
		}
	}

	protected boolean useMinimalAgents() {
		if (!canUseMinimalAgents)
			return false;
		if (parent != null && parent != this && !getParent().useMinimalAgents())
			return false;
		if (!hasFacet("use_regular_agents"))
			return GamaPreferences.AGENT_OPTIMIZATION.getValue();
		return FALSE.equals(getLitteral("use_regular_agents"));
	}

	protected void addBehavior(final StatementDescription r) {
		final String behaviorName = r.getName();
		if (behaviors == null) {
			behaviors = new TOrderedHashMap<String, StatementDescription>();
		}
		final StatementDescription existing = behaviors.get(behaviorName);
		if (existing != null) {
			if (existing.getKeyword().equals(r.getKeyword())) {
				duplicateInfo(r, existing);
			}
		}
		behaviors.put(behaviorName, r);
	}

	public boolean hasBehavior(final String a) {
		return behaviors != null && behaviors.containsKey(a)
				|| parent != null && parent != this && getParent().hasBehavior(a);
	}

	public StatementDescription getBehavior(final String aName) {
		StatementDescription ownBehavior = behaviors == null ? null : behaviors.get(aName);
		if (ownBehavior == null && parent != null && parent != this)
			ownBehavior = getParent().getBehavior(aName);
		return ownBehavior;
	}

	private void addAspect(final StatementDescription ce) {
		String aspectName = ce.getName();
		if (aspectName == null) {
			aspectName = DEFAULT;
			ce.setName(aspectName);
		}
		if (!aspectName.equals(DEFAULT) && hasAspect(aspectName)) {
			duplicateInfo(ce, getAspect(aspectName));
		}
		if (aspects == null) {
			aspects = new TOrderedHashMap<String, StatementDescription>();
		}
		aspects.put(aspectName, ce);
	}

	public StatementDescription getAspect(final String aName) {
		StatementDescription ownAspect = aspects == null ? null : aspects.get(aName);
		if (ownAspect == null && parent != null && parent != this)
			ownAspect = getParent().getAspect(aName);
		return ownAspect;
	}

	public Collection<String> getBehaviorNames() {
		final Collection<String> ownNames = behaviors == null ? new LinkedHashSet()
				: new LinkedHashSet(behaviors.keySet());
		if (parent != null && parent != this)
			ownNames.addAll(getParent().getBehaviorNames());
		return ownNames;
	}

	public Collection<String> getAspectNames() {
		final Collection<String> ownNames = aspects == null ? new LinkedHashSet() : new LinkedHashSet(aspects.keySet());
		if (parent != null && parent != this)
			ownNames.addAll(getParent().getAspectNames());
		return ownNames;

	}

	public Collection<StatementDescription> getAspects() {
		final Collection<StatementDescription> allAspects = new ArrayList();
		final Collection<String> aspectNames = getAspectNames();
		for (final String name : aspectNames) {
			allAspects.add(getAspect(name));
		}
		return allAspects;
	}

	public IArchitecture getControl() {
		return control;
	}

	public boolean hasAspect(final String a) {
		return aspects != null && aspects.containsKey(a)
				|| parent != null && parent != this && getParent().hasAspect(a);
	}

	@Override
	public SpeciesDescription getSpeciesContext() {
		return this;
	}

	public SpeciesDescription getMicroSpecies(final String name) {
		if (hasMicroSpecies()) {
			final SpeciesDescription retVal = microSpecies.get(name);
			if (retVal != null) {
				return retVal;
			}
		}
		// Takes care of invalid species (see Issue 711)
		if (parent != null && parent != this) {
			return getParent().getMicroSpecies(name);
		}
		return null;
	}

	@Override
	public String toString() {
		return "Description of " + getName();
	}

	public IAgentConstructor getAgentConstructor() {
		if (agentConstructor == null && parent != null && parent != this) {
			if (getParent().getJavaBase() == getJavaBase()) {
				agentConstructor = getParent().getAgentConstructor();
			} else {
				agentConstructor = IAgentConstructor.CONSTRUCTORS.get(getJavaBase());
			}
		}
		return agentConstructor;
	}

	protected void setAgentConstructor(final IAgentConstructor agentConstructor) {
		this.agentConstructor = agentConstructor;
	}

	public void addSkill(final Class<? extends ISkill> c, final ISkill instance) {
		if (c != null && !c.isInterface() && !Modifier.isAbstract(c.getModifiers())) {
			if (skills == null)
				skills = new HashMap();
			skills.put(c, instance);
		}
	}

	@Override
	public Set<Class<? extends ISkill>> getSkillClasses() {
		return skills == null ? Collections.EMPTY_SET : skills.keySet();
	}

	public SpeciesDescription getMacroSpecies() {
		final IDescription d = getEnclosingDescription();
		if (d instanceof SpeciesDescription) {
			return (SpeciesDescription) d;
		}
		return null;
	}

	@Override
	public SpeciesDescription getParent() {
		return (SpeciesDescription) super.getParent();
	}

	@Override
	public void inheritFromParent() {
		final SpeciesDescription parent = getParent();
		// Takes care of invalid species (see Issue 711)
		// built-in parents are not considered as their actions/variables are
		// normally already copied as java additions
		if (parent != null && parent.getJavaBase() == null) {
			error("Species " + parent.getName() + " Java base class can not be found. No validation is possible.",
					IGamlIssue.GENERAL);
			return;
		}
		if (getJavaBase() == null) {
			error("Species " + getName() + " Java base class can not be found. No validation is possible.",
					IGamlIssue.GENERAL);
			return;
		}
		if (parent != null && parent != this && !parent.isBuiltIn() && parent.getJavaBase() != null) {
			if (!parent.getJavaBase().isAssignableFrom(getJavaBase())) {
				error("Species " + getName() + " Java base class (" + getJavaBase().getSimpleName()
						+ ") is not a subclass of its parent species " + parent.getName() + " base class ("
						+ parent.getJavaBase().getSimpleName() + ")", IGamlIssue.GENERAL);
			}
			inheritMicroSpecies(parent);
			super.inheritFromParent();
		}

	}

	// FIXME HACK !
	private void inheritMicroSpecies(final SpeciesDescription parent) {
		// Takes care of invalid species (see Issue 711)
		if (parent == null || parent == this) {
			return;
		}
		if (parent.hasMicroSpecies())
			for (final Map.Entry<String, SpeciesDescription> entry : parent.getMicroSpecies().entrySet()) {
				if (!getMicroSpecies().containsKey(entry.getKey())) {
					getMicroSpecies().put(entry.getKey(), entry.getValue());
				}
			}
	}

	public boolean isGrid() {
		return getKeyword().equals(GRID);
	}

	@Override
	public String getTitle() {
		return getKeyword() + " " + getName();
	}

	@Override
	public String getDocumentation() {
		final StringBuilder sb = new StringBuilder(200);
		sb.append(getDocumentationWithoutMeta());
		sb.append(getMeta().getDocumentation());
		return sb.toString();
	}

	public String getDocumentationWithoutMeta() {
		final StringBuilder sb = new StringBuilder(200);
		final String parentName = getParent() == null ? "nil" : getParent().getName();
		final String hostName = getMacroSpecies() == null ? null : getMacroSpecies().getName();
		sb.append("<b>Subspecies of:</b> ").append(parentName).append("<br>");
		if (hostName != null) {
			sb.append("<b>Microspecies of:</b> ").append(hostName).append("<br>");
		}
		sb.append("<b>Skills:</b> ").append(getSkillsNames()).append("<br>");
		sb.append("<b>Attributes:</b> ").append(getAttributeNames()).append("<br>");
		sb.append("<b>Actions: </b>").append(getActionNames()).append("<br>");
		sb.append("<br/>");
		return sb.toString();
	}

	public Set<String> getSkillsNames() {
		if (skills == null)
			return Collections.EMPTY_SET;
		final Set<String> names = new TLinkedHashSet();

		for (final ISkill skill : skills.values()) {
			if (skill != null) {
				names.add(AbstractGamlAdditions.getSkillNameFor(skill.getClass()));
			}
		}
		// Takes care of invalid species (see Issue 711)
		if (parent != null && parent != this) {
			names.addAll(getParent().getSkillsNames());
		}
		return names;
	}

	/**
	 * Returns the constant expression representing this species
	 */
	public SpeciesConstantExpression getSpeciesExpr() {
		if (speciesExpr == null) {
			final IType type = GamaType.from(SpeciesDescription.this);
			speciesExpr = GAML.getExpressionFactory().createSpeciesConstant(type);
		}
		return speciesExpr;
	}

	public void visitMicroSpecies(final DescriptionVisitor<SpeciesDescription> visitor) {
		if (!hasMicroSpecies())
			return;
		getMicroSpecies().forEachValue(visitor);
	}

	@Override
	public void setParent(final TypeDescription parent) {
		super.setParent(parent);
		if (!isBuiltIn())
			if (!verifyParent()) {
				super.setParent(null);
				return;
			}
		if (parent instanceof SpeciesDescription && parent != this && !canUseMinimalAgents && !parent.isBuiltIn()) {
			((SpeciesDescription) parent).invalidateMinimalAgents();
		}
	}

	/**
	 * Verifies if the specified species can be a parent of this species.
	 *
	 * A species can be parent of other if the following conditions are hold 1.
	 * A parent species is visible to the sub-species. 2. A species can' be a
	 * sub-species of itself. 3. 2 species can't be parent of each other. 5. A
	 * species can't be a sub-species of its direct/in-direct micro-species. 6.
	 * A species and its direct/indirect micro/macro-species can't share
	 * one/some direct/indirect parent-species having micro-species. 7. The
	 * inheritance between species from different branches doesn't form a
	 * "circular" inheritance.
	 *
	 * @param parentName
	 *            the name of the potential parent
	 * @throws GamlException
	 *             if the species with the specified name can not be a parent of
	 *             this species.
	 */
	protected boolean verifyParent() {
		if (parent == null) {
			return true;
		}
		if (this == parent) {
			error(getName() + " species can't be a sub-species of itself", IGamlIssue.GENERAL);
			return false;
		}
		if (parentIsAmongTheMicroSpecies()) {
			error(getName() + " species can't be a sub-species of one of its micro-species", IGamlIssue.GENERAL);
			return false;
		}
		if (!parentIsVisible()) {
			error(parent.getName() + " can't be a parent species of " + this.getName() + " species.",
					IGamlIssue.WRONG_PARENT, PARENT);
			return false;
		}
		if (hierarchyContainsSelf()) {
			error(this.getName() + " species and " + parent.getName() + " species can't be sub-species of each other.");
			return false;
		}
		return true;
	}

	private boolean parentIsAmongTheMicroSpecies() {
		final boolean[] result = new boolean[1];
		visitMicroSpecies(new DescriptionVisitor<SpeciesDescription>() {

			@Override
			public boolean visit(final SpeciesDescription desc) {
				if (desc == parent) {
					result[0] = true;
					return false;
				} else
					desc.visitMicroSpecies(this);
				return true;
			}
		});
		return result[0];
	}

	private boolean hierarchyContainsSelf() {
		SpeciesDescription currentSpeciesDesc = this;
		while (currentSpeciesDesc != null) {
			final SpeciesDescription p = currentSpeciesDesc.getParent();
			// Takes care of invalid species (see Issue 711)
			if (p == currentSpeciesDesc || p == this) {
				return true;
			} else {
				currentSpeciesDesc = p;
			}
		}
		return false;
	}

	protected boolean parentIsVisible() {
		if (getParent().isExperiment())
			return false;
		SpeciesDescription host = getMacroSpecies();
		while (host != null) {
			if (host == parent || host.getMicroSpecies(parent.getName()) != null) {
				return true;
			} else
				host = host.getMacroSpecies();
		}
		return false;
	}

	/**
	 * Finalizes the species description + Copy the behaviors, attributes from
	 * parent; + Creates the control if necessary. Add a variable representing
	 * the population of each micro-species
	 *
	 * @throws GamlException
	 */
	public void finalizeDescription() {
		if (isMirror()) {
			addChild(DescriptionFactory.create(AGENT, this, NAME, TARGET, TYPE,
					String.valueOf(ITypeProvider.MIRROR_TYPE)));
		}

		// Add the control if it is not already added
		finalizeControl();
		final boolean isBuiltIn = this.isBuiltIn();

		final DescriptionVisitor<SpeciesDescription> visitor = new DescriptionVisitor<SpeciesDescription>() {

			@Override
			public boolean visit(final SpeciesDescription microSpec) {
				microSpec.finalizeDescription();
				if (!microSpec.isExperiment() && !isBuiltIn) {
					final VariableDescription var = (VariableDescription) DescriptionFactory.create(CONTAINER,
							SpeciesDescription.this, NAME, microSpec.getName());
					var.setSyntheticSpeciesContainer();
					var.setFacet(OF, GAML.getExpressionFactory()
							.createTypeExpression(getModelDescription().getTypeNamed(microSpec.getName())));
					final Set<String> dependencies = new HashSet();
					if (attributes != null)
						for (final VariableDescription v : microSpec.attributes.values()) {
							v.getExtraDependencies(dependencies);
						}
					dependencies.add(SHAPE);
					dependencies.add(LOCATION);
					var.addDependenciesNames(dependencies);
					final GamaHelper get = new GamaHelper() {

						@Override
						public Object run(final IScope scope, final IAgent agent, final IVarAndActionSupport skill,
								final Object... values) throws GamaRuntimeException {
							// TODO Make a test ?
							return ((IMacroAgent) agent).getMicroPopulation(microSpec.getName());
						}
					};
					final GamaHelper set = new GamaHelper() {

						@Override
						public Object run(final IScope scope, final IAgent agent, final IVarAndActionSupport target,
								final Object... value) throws GamaRuntimeException {
							return null;
						}

					};
					final GamaHelper init = new GamaHelper(null) {

						@Override
						public Object run(final IScope scope, final IAgent agent, final IVarAndActionSupport skill,
								final Object... values) throws GamaRuntimeException {
							((IMacroAgent) agent).initializeMicroPopulation(scope, microSpec.getName());
							return ((IMacroAgent) agent).getMicroPopulation(microSpec.getName());
						}

					};
					var.addHelpers(get, init, set);
					addChild(var);
				}
				return true;
			}
		};

		// recursively finalize the sorted micro-species
		visitMicroSpecies(visitor);
		sortAttributes();
	}

	/**
	 *
	 */
	private void finalizeControl() {
		if (control == null && parent != this && parent instanceof SpeciesDescription) {
			control = ((SpeciesDescription) parent).getControl();
			if (control != null) {
				control = (IArchitecture) control.duplicate();
			}
		}
		if (control == null) {
			control = (IArchitecture) AbstractGamlAdditions.getSkillInstanceFor(REFLEX);
			return;
		}
		Class<? extends ISkill> clazz = control.getClass();
		while (clazz != AbstractArchitecture.class) {
			addSkill(clazz, control);
			clazz = (Class<? extends ISkill>) clazz.getSuperclass();
		}

	}

	@Override
	protected void validateChildren() {
		// We try to issue information about the state of the species: at first,
		// abstract.

		for (final StatementDescription a : getActions()) {
			if (a.isAbstract()) {
				this.info("Action '" + a.getName() + "' is defined or inherited as virtual. In consequence, "
						+ getName() + " is considered as abstract and cannot be instantiated.",
						IGamlIssue.MISSING_ACTION);
			}
		}

		super.validateChildren();
	}

	public boolean isExperiment() {
		return false;
	}

	public boolean isModel() {
		return false;
	}

	public boolean hasMicroSpecies() {
		return microSpecies != null;
	}

	public TOrderedHashMap<String, SpeciesDescription> getMicroSpecies() {
		if (microSpecies == null) {
			microSpecies = new TOrderedHashMap<String, SpeciesDescription>();
		}
		return microSpecies;
	}

	public boolean isMirror() {
		return hasFacet(MIRRORS);
	}

	public Boolean implementsSkill(final String skill) {
		if (skills == null)
			return false;
		return skills.containsKey(AbstractGamlAdditions.getSkillClassFor(skill));
	}

	public Class<? extends IAgent> getJavaBase() {
		if (javaBase == null) {
			if (parent != null && parent != this && !getParent().getName().equals(AGENT)) {
				javaBase = getParent().getJavaBase();
			} else {
				if (useMinimalAgents()) {
					javaBase = isGrid() ? MinimalGridAgent.class : MinimalAgent.class;
				} else {
					javaBase = isGrid() ? GamlGridAgent.class : GamlAgent.class;
				}
			}
		}
		return javaBase;
	}

	protected void setJavaBase(final Class javaBase) {
		this.javaBase = javaBase;
	}

	/**
	 * @param found_sd
	 * @return
	 */
	public boolean hasMacroSpecies(final SpeciesDescription found_sd) {
		final SpeciesDescription sd = getMacroSpecies();
		if (sd == null) {
			return false;
		}
		if (sd.equals(found_sd)) {
			return true;
		}
		return sd.hasMacroSpecies(found_sd);
	}

	/**
	 * @param macro
	 * @return
	 */
	public boolean hasParent(final SpeciesDescription p) {
		final SpeciesDescription sd = getParent();
		// Takes care of invalid species (see Issue 711)
		if (sd == null || sd == this) {
			return false;
		}
		if (sd.equals(p)) {
			return true;
		}
		return sd.hasParent(p);
	}

	@Override
	public boolean visitOwnChildren(final DescriptionVisitor visitor) {
		boolean result = super.visitOwnChildren(visitor);
		if (!result)
			return false;
		if (microSpecies != null) {
			result &= microSpecies.forEachValue(visitor);
		}
		if (!result)
			return false;
		if (behaviors != null)
			result &= behaviors.forEachValue(visitor);
		if (!result)
			return false;
		if (aspects != null)
			result &= aspects.forEachValue(visitor);
		return result;
	}

	@Override
	public boolean visitChildren(final DescriptionVisitor visitor) {
		boolean result = super.visitChildren(visitor);
		if (!result)
			return false;
		if (hasMicroSpecies()) {
			result &= microSpecies.forEachValue(visitor);
		}
		if (!result)
			return false;
		for (final IDescription d : getBehaviors()) {
			result &= visitor.visit(d);
			if (!result)
				return false;
		}
		for (final IDescription d : getAspects()) {
			result &= visitor.visit(d);
			if (!result)
				return false;
		}
		return result;
	}

	/**
	 * @return
	 */
	public Collection<StatementDescription> getBehaviors() {
		final Collection<String> names = getBehaviorNames();
		final Collection<StatementDescription> result = new ArrayList();
		for (final String name : names) {
			result.add(getBehavior(name));
		}
		return result;
	}

	@Override
	public void collectMetaInformation(final GamlProperties meta) {
		super.collectMetaInformation(meta);
		if (isBuiltIn()) {
			meta.put(GamlProperties.SPECIES, getName());
		}
	}

}
