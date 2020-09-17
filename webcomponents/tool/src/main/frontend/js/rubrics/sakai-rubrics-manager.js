import {RubricsElement} from "./rubrics-element.js";
import {html} from "/webcomponents/assets/lit-element/lit-element.js";
import {SakaiRubricsLanguage, tr} from "./sakai-rubrics-language.js";
import {SakaiRubricsList} from "./sakai-rubrics-list.js";
import {SakaiRubricsSharedList} from "./sakai-rubrics-shared-list.js";
import {loadProperties} from "/webcomponents/sakai-i18n.js";

class SakaiRubricsManager extends RubricsElement {

  constructor() {

    super();

    this.siteRubricsExpanded = "false";
    this.sharedRubricsExpanded = "false";

    SakaiRubricsLanguage.loadTranslations().then(result => this.i18nLoaded = result );
  }

  static get properties() {
    return { token: String, i18nLoaded: Boolean };
  }

  shouldUpdate(changedProperties) {
    return this.i18nLoaded;
  }

  render() {

    return html`
      <h1>${tr("manage_rubrics")}</h1>

      <div class="row">
        <div class="col-md-4 form-group">
          <label for="rubrics-search-bar"><sr-lang key="search_rubrics">Search Rubrics by title or author</sr-lang></label>
          <input type="text" id="rubrics-search-bar" name="rubrics-search-bar" class="form-control" @keyup="${this.filterRubrics}">
        </div>
      </div>

      <div role="tablist">
        <div id="site-rubrics-title" aria-expanded="${this.siteRubricsExpanded}"
            role="tab" aria-multiselectable="true" class="manager-collapse-title"
            title="${tr("toggle_site_rubrics")}" tabindex="0" @click="${this.toggleSiteRubrics}">
          <div>
            <span class="collpase-icon fa fa-chevron-down"></span>
            <sr-lang key="site_rubrics">site_rubrics</sr-lang>
          </div>
        </div>

      <div role="tabpanel" aria-labelledby="site-rubrics-title" id="site_rubrics">
        <div class="rubric-title-sorting">
          <div @click="${this.sortSiteRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="site_name" key="site_name">site_name</sr-lang><span class="collpase-icon fa fa-chevron-up site_name sort_element_site"></span></a></div>
          <div @click="${this.sortSiteRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="site_title" key="site_title">site_title</sr-lang><span class="site_title sort_element_site"></span></a></div>
          <div @click="${this.sortSiteRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="creator_name" key="creator_name">creator_name</sr-lang><span class="creator_name sort_element_site"></span></a></div>
          <div @click="${this.sortSiteRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="modified" key="modified">modified</sr-lang><span class="modified sort_element_site"></span></a></div>
          <div class="actions"><sr-lang key="actions">actions</sr-lang></div>
        </div>
        <br>
        <sakai-rubrics-list id="sakai-rubrics" @sharing-change="${this.handleSharingChange}" @copy-share-site="${this.copyShareSite}" token="Bearer ${this.token}"></sakai-rubrics-list>
      </div>

        <div id="shared-rubrics-title" aria-expanded="${this.sharedRubricsExpanded}" role="tab" aria-multiselectable="true" class="manager-collapse-title" title="${tr("toggle_shared_rubrics")}" tabindex="0" @click="${this.toggleSharedRubrics}">
          <div>
            <span class="collpase-icon fa fa-chevron-down"></span>
            <sr-lang key="shared_rubrics">shared_rubrics</sr-lang>
          </div>
        </div>

      <div role="tabpanel" aria-labelledby="shared-rubrics-title" id="shared_rubrics">
        <div id="sharedlist">
          <div class="rubric-title-sorting">
          <div @click="${this.sortSharedRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="site_name" key="site_name">site_name</sr-lang><span class="collpase-icon fa fa-chevron-up site_name sort_element_shared"></span></a></div>
          <div @click="${this.sortSharedRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="site_title" key="site_title">site_title</sr-lang><span class="site_title sort_element_shared"></span></a></div>
          <div @click="${this.sortSharedRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="creator_name" key="creator_name">creator_name</sr-lang><span class="creator_name sort_element_shared"></span></a></div>
          <div @click="${this.sortSharedRubrics}"><a href="javascript:void(0)" style="text-decoration: none;"><sr-lang class="modified" key="modified">modified</sr-lang><span class="modified sort_element_shared"></span></a></div>
          <div class="actions"><sr-lang key="actions">actions</sr-lang></div>
        </div>
        <br>
        <sakai-rubrics-shared-list token="Bearer ${this.token}" id="sakai-rubrics-shared-list" @copy-share-site="${this.copyShareSite}" ></sakai-rubrics-shared-list>
      </div>
      <br>
      </div>

      </div>
    `;
  }

  handleSharingChange(e) {
    document.getElementById("sakai-rubrics-shared-list").refresh();
  }

  copyShareSite() {
    this.querySelector("sakai-rubrics-list").refresh();
  }

  toggleSiteRubrics() {

    var siteRubrics = $("#site_rubrics");
    siteRubrics.toggle();
    var icon = $("#site-rubrics-title .collpase-icon");
    if (siteRubrics.is(":visible")) {
      this.siteRubricsExpanded = "true";
      icon.removeClass("fa-chevron-right").addClass("fa-chevron-down");
    } else {
      this.siteRubricsExpanded = "false";
      icon.removeClass("fa-chevron-down").addClass("fa-chevron-right");
    }
  }

  toggleSharedRubrics() {

    var sharedRubrics = $("#shared_rubrics");
    sharedRubrics.toggle();
    var icon = $("#shared-rubrics-title .collpase-icon");
    if (sharedRubrics.is(":visible")) {
      this.sharedRubricsExpanded = "true";
      icon.removeClass("fa-chevron-right").addClass("fa-chevron-down");
    } else {
      this.sharedRubricsExpanded = "false";
      icon.removeClass("fa-chevron-down").addClass("fa-chevron-right");
    }
  }

  filterRubrics() {
    var searchInput = document.getElementById('rubrics-search-bar');
    var searchInputValue = searchInput.value.toLowerCase();

    this.querySelectorAll('sakai-rubrics-list, sakai-rubrics-shared-list').forEach(rubricList => {
      rubricList.querySelectorAll('.rubric-item').forEach(rubricItem => {
        rubricItem.classList.remove('hidden');
        var rubricTitle = rubricItem.querySelector('.rubric-name').textContent;
        var rubricAuthor = rubricItem.querySelector('sakai-rubric-creator-name').textContent;
        var rubricSite = rubricItem.querySelector('sakai-rubric-site-title').textContent;
        if (!rubricAuthor.toLowerCase().includes(searchInputValue) &&
            !rubricTitle.toLowerCase().includes(searchInputValue) &&
            !rubricSite.toLowerCase().includes(searchInputValue)
        ) {
          rubricItem.classList.add('hidden');
        }
      });
    });
  }

  sortSiteRubrics(event) {
    var sortInput = event.target.className;
    var sortInputValue = sortInput.toString().toLowerCase();
    var ascending = true; 
    //icon from header
    $('.sort_element_site').each( function( i, val ) {
      var className = $( val ).attr('class');
      if (!className.includes(sortInputValue)) {
        $( val ).removeClass('collpase-icon fa fa-chevron-down');
        $( val ).removeClass('collpase-icon fa fa-chevron-up');
      }
    });
    switch (sortInputValue) {
      case 'site_name':
        if ($('.site_name.sort_element_site').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.site_name.sort_element_site').removeClass('collpase-icon fa fa-chevron-up');
          $('.site_name.sort_element_site').addClass('collpase-icon fa fa-chevron-down');
          ascending = false; 
        } else {
          $('.site_name.sort_element_site').removeClass('collpase-icon fa fa-chevron-down');
          $('.site_name.sort_element_site').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
      case 'site_title':
        if ($('.site_title.sort_element_site').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.site_title.sort_element_site').removeClass('collpase-icon fa fa-chevron-up');
          $('.site_title.sort_element_site').addClass('collpase-icon fa fa-chevron-down');
          ascending = false;
        } else {
          $('.site_title.sort_element_site').removeClass('collpase-icon fa fa-chevron-down');
          $('.site_title.sort_element_site').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
      case 'creator_name':
        if ($('.creator_name.sort_element_site').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.creator_name.sort_element_site').removeClass('collpase-icon fa fa-chevron-up');
          $('.creator_name.sort_element_site').addClass('collpase-icon fa fa-chevron-down');
          ascending = false;
        } else {
          $('.creator_name.sort_element_site').removeClass('collpase-icon fa fa-chevron-down');
          $('.creator_name.sort_element_site').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
      case 'modified':
        if ($('.modified.sort_element_site').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.modified.sort_element_site').removeClass('collpase-icon fa fa-chevron-up');
          $('.modified.sort_element_site').addClass('collpase-icon fa fa-chevron-down');
          ascending = false;
        } else {
          $('.modified.sort_element_site').removeClass('collpase-icon fa fa-chevron-down');
          $('.modified.sort_element_site').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
    } 

    let elementChildSite= this.querySelector("sakai-rubrics-list");
    elementChildSite.sortRubrics(sortInputValue, ascending);
  }

  sortSharedRubrics(event) {
    var sortInput = event.target.className;
    var sortInputValue = sortInput.toString().toLowerCase();
    var ascending = true; 
    //icon from header
    $('.sort_element_shared').each( function( i, val ) {
      var className = $( val ).attr('class');
      if (!className.includes(sortInputValue)) {
        $( val ).removeClass('collpase-icon fa fa-chevron-down');
        $( val ).removeClass('collpase-icon fa fa-chevron-up');
      }
    });
    switch (sortInputValue) {
      case 'site_name':
        if ($('.site_name.sort_element_shared').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.site_name.sort_element_shared').removeClass('collpase-icon fa fa-chevron-up');
          $('.site_name.sort_element_shared').addClass('collpase-icon fa fa-chevron-down');
          ascending = false; 
        } else {
          $('.site_name.sort_element_shared').removeClass('collpase-icon fa fa-chevron-down');
          $('.site_name.sort_element_shared').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
      case 'site_title':
        if ($('.site_title.sort_element_shared').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.site_title.sort_element_shared').removeClass('collpase-icon fa fa-chevron-up');
          $('.site_title.sort_element_shared').addClass('collpase-icon fa fa-chevron-down');
          ascending = false;
        } else {
          $('.site_title.sort_element_shared').removeClass('collpase-icon fa fa-chevron-down');
          $('.site_title.sort_element_shared').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
      case 'creator_name':
        if ($('.creator_name.sort_element_shared').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.creator_name.sort_element_shared').removeClass('collpase-icon fa fa-chevron-up');
          $('.creator_name.sort_element_shared').addClass('collpase-icon fa fa-chevron-down');
          ascending = false;
        } else {
          $('.creator_name.sort_element_shared').removeClass('collpase-icon fa fa-chevron-down');
          $('.creator_name.sort_element_shared').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
      case 'modified':
        if ($('.modified.sort_element_shared').hasClass('collpase-icon fa fa-chevron-up')) {
          $('.modified.sort_element_shared').removeClass('collpase-icon fa fa-chevron-up');
          $('.modified.sort_element_shared').addClass('collpase-icon fa fa-chevron-down');
          ascending = false;
        } else {
          $('.modified.sort_element_shared').removeClass('collpase-icon fa fa-chevron-down');
          $('.modified.sort_element_shared').addClass('collpase-icon fa fa-chevron-up');
          ascending = true;
        }
        break;
    } 

    let elementChildShared= this.querySelector("sakai-rubrics-shared-list");
    elementChildShared.sortRubrics(sortInputValue, ascending);
  }
 
}

customElements.define("sakai-rubrics-manager", SakaiRubricsManager);
