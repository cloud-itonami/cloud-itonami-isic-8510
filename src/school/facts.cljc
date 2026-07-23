(ns school.facts
  "Per-jurisdiction pre-primary/primary-education licensing catalog --
  the G2-style spec-basis table the Curriculum Safeguarding Governor
  checks every jurisdiction/assess proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's school-
  licensing/safeguarding requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official school-
  education regulator (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a
  real source, done -- never invent a jurisdiction's requirements to
  make coverage look bigger.

  The USA entry cites individual state Departments of Education (the
  actual licensing authority for compulsory schooling; the US has no
  single federal school-operator licensor) operating under the Every
  Student Succeeds Act (ESSA, Pub. L. 114-95) framework, the same
  honest representative-citation posture every prior federated-
  jurisdiction catalog in this fleet takes. The GBR entry cites the
  Department for Education (DfE) and Ofsted (Office for Standards in
  Education, Children's Services and Skills) jointly, since school
  registration (DfE) and safeguarding/quality inspection (Ofsted) are
  two distinct statutory functions -- both cited rather than
  collapsed into one.

  The CAN entry cites Ontario's Education Act, R.S.O. 1990, c. E.2 as
  a REPRESENTATIVE PROVINCE-LEVEL source, not a 'Canada' federal
  claim: K-12 education in Canada is constitutionally a provincial/
  territorial matter (unlike USA's ESSA, which is a federal statute
  even though enforcement is state-by-state), so there is no federal
  Ministry of Education or national school-operator licensor to cite.
  Ontario was chosen as the representative province and its Education
  Act was fetched and read directly this session (live e-laws page
  plus a 2026-06-26 Wayback Machine capture used as a readable mirror
  because the live ontario.ca/laws SPA shell requires client-side
  JavaScript to render statute text -- not a bot-detection block, but
  curl cannot render it either way, so the archived capture was used
  as the actually-read source). Other provinces (Quebec's Loi sur
  l'instruction publique, British Columbia's School Act, etc.) are
  NOT covered by this entry and must not be assumed equivalent.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  enrollment-record/curriculum-approval/safeguarding-policy/staff-
  background-check evidence set submitted in some form;
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any :jurisdiction/assess
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "文部科学省 (Ministry of Education, Culture, Sports, Science and Technology, MEXT)"
          :legal-basis "学校教育法 (School Education Act)"
          :national-spec "設置基準・学齢簿・児童虐待防止対応に係る学校運営基準"
          :provenance "https://www.mext.go.jp/"
          :required-evidence ["学齢簿記載事項証明書 (student-registration record)"
                              "教育課程編成届 (curriculum-approval certificate)"
                              "いじめ防止基本方針/児童虐待防止対応方針 (safeguarding-policy document)"
                              "職員身元確認証明書 (staff-background-check certification)"]}
   "USA" {:name "United States"
          :owner-authority "State Departments of Education (compulsory-education licensing authority)"
          :legal-basis "State compulsory-education codes under the Every Student Succeeds Act (ESSA, Pub. L. 114-95) framework"
          :national-spec "State school-registration, curriculum-approval and child-safeguarding requirements"
          :provenance "https://www.ed.gov/laws-and-policy/every-student-succeeds-act"
          :required-evidence ["Student-registration record"
                              "Curriculum-approval certificate"
                              "Safeguarding/child-protection policy document"
                              "Staff-background-check certification"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Department for Education (DfE) / Ofsted (school registration + safeguarding inspection)"
          :legal-basis "Education Act 2002 s.175 / Children Act 2004 (safeguarding duties)"
          :national-spec "Independent School Standards / Ofsted School Inspection Handbook safeguarding criteria"
          :provenance "https://www.gov.uk/government/organisations/ofsted"
          :required-evidence ["Student-registration record"
                              "Curriculum-approval certificate"
                              "Safeguarding/child-protection policy document"
                              "Staff-background-check certification (DBS)"]}
   "DEU" {:name "Germany"
          :owner-authority "Kultusministerien der Länder (state ministries of education and cultural affairs)"
          :legal-basis "Schulgesetze der Länder (state School Acts)"
          :national-spec "Schulanmeldung, Lehrplangenehmigung und Kinderschutz-Vorgaben der Länder"
          :provenance "https://www.kmk.org/"
          :required-evidence ["Schulanmeldung (student-registration record)"
                              "Lehrplangenehmigung (curriculum-approval certificate)"
                              "Kinderschutzkonzept (safeguarding-policy document)"
                              "Führungszeugnis des Personals (staff-background-check certification)"]}
   "CAN" {:name "Canada (Ontario) -- province-level entry, NOT a federal claim (education is constitutionally provincial/territorial in Canada; Ontario is cited as one representative province, not 'Canada' nationally)"
          :owner-authority "Ontario Ministry of Education / Minister of Education (provincial authority; Canada has no federal Ministry of Education or national school-operator licensor)"
          :legal-basis "Education Act, R.S.O. 1990, c. E.2 (Province of Ontario) -- s. 16 (notice of intention to operate a private school) and s. 8 (1), par. 2 (Minister's authority to prescribe courses of study for the primary/junior/intermediate/senior divisions)"
          :national-spec "Private-school registration via annual notice of intention to operate (due 1 September, s. 16 (1)-(2), offence to operate without filing per s. 16 (4)), Ministry inspection power (s. 16 (6)), and Minister-prescribed curriculum for the primary division (s. 8 (1), par. 2)"
          :provenance "https://www.ontario.ca/laws/statute/90e02"
          :required-evidence ["Notice of intention to operate a private school, filed annually with the Ministry on or before 1 September (school-registration record) -- Education Act s. 16 (1)-(2)"
                              "Statistical return of enrolment, staff and courses of study, as required by the Minister -- Education Act s. 16 (5)"
                              "Courses of study for the primary/junior/intermediate/senior divisions as prescribed or approved by the Minister (curriculum-approval basis) -- Education Act s. 8 (1), par. 2"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to finalize a
  promotion or a safeguarding record on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8510 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `school.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
