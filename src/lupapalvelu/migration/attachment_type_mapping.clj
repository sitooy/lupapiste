(ns lupapalvelu.migration.attachment-type-mapping)

(def attachment-mapping
  {{:type-group :ennakkoluvat_ja_lausunnot :type-id :naapurien_suostumukset}    {:type-group :ennakkoluvat_ja_lausunnot :type-id :naapurin_suostumus},
   {:type-group :ennakkoluvat_ja_lausunnot :type-id :selvitys_naapurien_kuulemisesta}    {:type-group :ennakkoluvat_ja_lausunnot :type-id :naapurin_kuuleminen},
   {:type-group :osapuolet :type-id :paa_ja_rakennussuunnittelijan_tiedot}    {:type-group :osapuolet :type-id :suunnittelijan_tiedot},
   {:type-group :paapiirustus :type-id :julkisivupiirros}    {:type-group :paapiirustus :type-id :julkisivupiirustus},
   {:type-group :paapiirustus :type-id :leikkauspiirros}    {:type-group :paapiirustus :type-id :leikkauspiirustus},
   {:type-group :paapiirustus :type-id :pohjapiirros}    {:type-group :paapiirustus :type-id :pohjapiirustus},
   {:type-group :paapiirustus :type-id :yhdistelmapiirros}    {:type-group :paapiirustus :type-id :muu_paapiirustus},
   {:type-group :rakennuspaikan_hallinta :type-id :ote_asunto-osakeyhtion_kokouksen_poytakirjasta}    {:type-group :rakennuspaikan_hallinta :type-id :ote_yhtiokokouksen_poytakirjasta},
   {:type-group :muut :type-id :jatevesijarjestelman_rakennustapaseloste}    {:type-group :suunnitelmat :type-id :jatevesijarjestelman_suunnitelma},
   {:type-group :rakennuspaikan_hallinta :type-id :kiinteiston_lohkominen}    {:type-group :rakennuspaikan_hallinta :type-id :kiinteiston_lohkominen},
   {:type-group :rakennuspaikka :type-id :tonttikartta_tarvittaessa}    {:type-group :rakennuspaikka :type-id :tonttikartta_tarvittaessa},
   {:type-group :muut :type-id :yhteistilat}    {:type-group :selvitykset :type-id :yhteistilat},
   {:type-group :muut :type-id :energiataloudellinen_selvitys}    {:type-group :selvitykset :type-id :energiataloudellinen_selvitys},
   {:type-group :muut :type-id :energiatodistus}    {:type-group :selvitykset :type-id :energiatodistus},
   {:type-group :muut :type-id :haittaaineet}    {:type-group :selvitykset :type-id :haittaaineselvitys},
   {:type-group :muut :type-id :kokoontumishuoneisto}    {:type-group :selvitykset :type-id :kokoontumishuoneisto},
   {:type-group :muut :type-id :liikkumis_ja_esteettomyysselvitys}    {:type-group :selvitykset :type-id :liikkumis_ja_esteettomyysselvitys},
   {:type-group :muut :type-id :lomarakennuksen_muutos_asuinrakennukseksi_selvitys_maaraysten_toteutumisesta}    {:type-group :selvitykset :type-id :lomarakennuksen_muutos_asuinrakennukseksi_selvitys_maaraysten_toteutumisesta},
   {:type-group :muut :type-id :maalampo_rakennettavuusselvitys}    {:type-group :selvitykset :type-id :maalampo_rakennettavuusselvitys},
   {:type-group :muut :type-id :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo}    {:type-group :selvitykset :type-id :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo},
   {:type-group :muut :type-id :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta}    {:type-group :selvitykset :type-id :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta},
   {:type-group :muut :type-id :riskianalyysi}    {:type-group :selvitykset :type-id :riskianalyysi},
   {:type-group :muut :type-id :selvitys_kiinteiston_jatehuollon_jarjestamisesta}    {:type-group :selvitykset :type-id :selvitys_kiinteiston_jatehuollon_jarjestamisesta},
   {:type-group :muut :type-id :selvitys_liittymisesta_ymparoivaan_rakennuskantaan}    {:type-group :selvitykset :type-id :selvitys_liittymisesta_ymparoivaan_rakennuskantaan},
   {:type-group :muut :type-id :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo}    {:type-group :selvitykset :type-id :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo},
   {:type-group :muut :type-id :selvitys_rakennuksen_aaniteknisesta_toimivuudesta}    {:type-group :selvitykset :type-id :selvitys_rakennuksen_aaniteknisesta_toimivuudesta},
   {:type-group :rakennuspaikka :type-id :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista}    {:type-group :selvitykset :type-id :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista},
   {:type-group :muut :type-id :selvitys_rakennuspaikan_terveellisyydesta}    {:type-group :selvitykset :type-id :selvitys_rakennuspaikan_terveellisyydesta},
   {:type-group :muut :type-id :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista}    {:type-group :selvitykset :type-id :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista},
   {:type-group :muut :type-id :hankeselvitys}    {:type-group :suunnitelmat :type-id :hankeselvitys},
   {:type-group :muut :type-id :julkisivujen_varityssuunnitelma}    {:type-group :suunnitelmat :type-id :julkisivujen_varityssuunnitelma},
   {:type-group :muut :type-id :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta}    {:type-group :suunnitelmat :type-id :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta},
   {:type-group :muut :type-id :mainoslaitesuunnitelma}    {:type-group :suunnitelmat :type-id :mainoslaitesuunnitelma},
   {:type-group :muut :type-id :piha_tai_istutussuunnitelma}    {:type-group :suunnitelmat :type-id :piha_tai_istutussuunnitelma},
   {:type-group :muut :type-id :valaistussuunnitelma}    {:type-group :suunnitelmat :type-id :valaistussuunnitelma},
   {:type-group :muut :type-id :hulevesisuunnitelma}    {:type-group :erityissuunnitelmat :type-id :hulevesisuunnitelma},
   {:type-group :muut :type-id :ikkunadetaljit}    {:type-group :erityissuunnitelmat :type-id :ikkunadetaljit},
   {:type-group :muut :type-id :kalliorakentamistekninen_suunnitelma}    {:type-group :erityissuunnitelmat :type-id :kalliorakentamistekninen_suunnitelma},
   {:type-group :muut :type-id :lammityslaitesuunnitelma}    {:type-group :erityissuunnitelmat :type-id :lammityslaitesuunnitelma},
   {:type-group :muut :type-id :pohjaveden_hallintasuunnitelma}    {:type-group :erityissuunnitelmat :type-id :pohjaveden_hallintasuunnitelma},
   {:type-group :muut :type-id :radontekninen_suunnitelma}    {:type-group :erityissuunnitelmat :type-id :radontekninen_suunnitelma},
   {:type-group :muut :type-id :rakennesuunnitelma}    {:type-group :erityissuunnitelmat :type-id :rakennesuunnitelma},
   {:type-group :muut :type-id :merkki_ja_turvavalaistussuunnitelma}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :merkki_ja_turvavalaistussuunnitelma},
   {:type-group :muut :type-id :paloturvallisuussuunnitelma}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :paloturvallisuussuunnitelma},
   {:type-group :muut :type-id :sammutusautomatiikkasuunnitelma}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :sammutusautomatiikkasuunnitelma},
   {:type-group :muut :type-id :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta},
   {:type-group :muut :type-id :turvallisuusselvitys}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :turvallisuusselvitys},
   {:type-group :muut :type-id :ilmoitus_vaestonsuojasta}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :ilmoitus_vaestonsuojasta},
   {:type-group :muut :type-id :vaestonsuojasuunnitelma}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :vaestonsuojasuunnitelma},
   {:type-group :muut :type-id :paatos}    {:type-group :paatoksenteko :type-id :paatos},
   {:type-group :muut :type-id :paatosote}    {:type-group :paatoksenteko :type-id :paatosote},
   {:type-group :muut :type-id :katselmuksen_tai_tarkastuksen_poytakirja}    {:type-group :katselmukset_ja_tarkastukset :type-id :katselmuksen_tai_tarkastuksen_poytakirja},
   {:type-group :muut :type-id :rakennuksen_tietomalli_BIM}    {:type-group :tietomallit :type-id :rakennuksen_tietomalli_BIM},
   {:type-group :muut :type-id :ympariston_tietomalli_BIM}    {:type-group :tietomallit :type-id :ympariston_tietomalli_BIM},
   {:type-group :rakentamisen_aikaiset :type-id :erityissuunnitelma}    {:type-group :selvitykset :type-id :muu_selvitys},
   {:type-group :muut :type-id :ilmanvaihtosuunnitelma}    {:type-group :erityissuunnitelmat :type-id :iv_suunnitelma},
   {:type-group :rakennuspaikka :type-id :kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma}    {:type-group :erityissuunnitelmat :type-id :kvv_suunnitelma},
   {:type-group :muut :type-id :palotekninen_selvitys}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :paloturvallisuussuunnitelma},
   {:type-group :muut :type-id :paloturvallisuusselvitys}    {:type-group :pelastusviranomaiselle_esitettavat_suunnitelmat :type-id :paloturvallisuussuunnitelma},
   {:type-group :muut :type-id :vesikattopiirustus}    {:type-group :erityissuunnitelmat :type-id :rakennesuunnitelma},
   {:type-group :muut :type-id :kerrosalaselvitys}    {:type-group :rakennuspaikka :type-id :rakennusoikeuslaskelma},
   {:type-group :muut :type-id :selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta}    {:type-group :erityissuunnitelmat :type-id :rakennesuunnitelma},
   {:type-group :muut :type-id :rakennetapaselvitys}    {:type-group :erityissuunnitelmat :type-id :rakennesuunnitelma},
   {:type-group :muut :type-id :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta}    {:type-group :selvitykset :type-id :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta},
   {:type-group :paapiirustus :type-id :paapiirustus}    {:type-group :paapiirustus :type-id :muu_paapiirustus},
   {:type-group :muut :type-id :korjausrakentamisen_energiaselvitys}    {:type-group :selvitykset :type-id :energiataloudellinen_selvitys},
   {:type-group :muut :type-id :rakennusautomaatiosuunnitelma}    {:type-group :suunnitelmat :type-id :muu_suunnitelma},
   {:type-group :rakennuspaikan_hallinta :type-id :jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta}    {:type-group :rakennuspaikan_hallinta :type-id :todistus_hallintaoikeudesta},
   {:type-group :rakennuspaikan_hallinta :type-id :jaljennos_myonnetyista_lainhuudoista}    {:type-group :rakennuspaikan_hallinta :type-id :todistus_hallintaoikeudesta},
   {:type-group :rakennuspaikan_hallinta :type-id :jaljennos_perunkirjasta}    {:type-group :rakennuspaikan_hallinta :type-id :todistus_hallintaoikeudesta},
   {:type-group :rakennuspaikan_hallinta :type-id :jaljennos_vuokrasopimuksesta}    {:type-group :rakennuspaikan_hallinta :type-id :todistus_hallintaoikeudesta}})

(def osapuoli-attachment-mapping
  {{:type-group :osapuolet :type-id :paa_ja_rakennussuunnittelijan_tiedot}    {:type-group :osapuolet :type-id :suunnittelijan_tiedot}})
