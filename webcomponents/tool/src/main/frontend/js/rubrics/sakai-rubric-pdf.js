import { RubricsElement } from "./rubrics-element.js";
import { SakaiRubricsLanguage } from "./sakai-rubrics-language.js";
import { tr } from "./sakai-rubrics-language.js";
import { html } from "/webcomponents/assets/lit-element/lit-element.js";
export class SakaiRubricPdf extends RubricsElement {
  constructor() {
    super();
    SakaiRubricsLanguage.loadTranslations().then(result => this.i18nLoaded = result);
  }

  static get properties() {
    return {
      rubricTitle: String,
      token: String,
      rubricId: String,
      toolId: String,
      entityId: String,
      evaluatedItemId: String
    };
  }

  render() {
    return html`<span class="hidden-sm hidden-xs sr-only"><sr-lang key="export_label" /></span>
            <a role="button" title="${tr("export_title", [this.rubricTitle])}" href="#0" tabindex="0" class="linkStyle pdf fa fa-file-pdf-o" @click="${this.exportPdfRubric}"></a>`;
  }

  exportPdfRubric(e) {
    e.stopPropagation();
    this.toolId ? this.pdfGradedRubric(e) : this.pdfRubric(e);
  }

  pdfRubric(e) {
    e.stopPropagation();
    let options = {
      method: "GET",
      headers: {
        "Authorization": this.token
      }
    };
    fetch(`/rubrics-service/rest/getPdf?sourceId=${this.rubricId}`, options).then(data => data.json()).then((data) => {
      var sampleArr = this.base64ToArrayBuffer(data);
      this.saveByteArray(this.rubricTitle, sampleArr);
    }).catch(reason => {
      console.log(`Failed to get the pdf` + reason);
    });
  }

  pdfGradedRubric(e) {
    let options = {
      method: "GET",
      headers: {
        "Authorization": this.token
      }
    };
    fetch(`/rubrics-service/rest/getGradedPdf?sourceId=${this.rubricId}&toolId=${this.toolId}&itemId=${this.entityId}&evaluatedItemId=${this.evaluatedItemId}`, options).then((data) => data.json()).then((data) => {
      var sampleArr = this.base64ToArrayBuffer(data);
      this.saveByteArray(this.rubricTitle, sampleArr);
    }).catch((reason) => {
      console.log(`Failed to get the pdf` + reason);
    });
  }

  base64ToArrayBuffer(base64) {
    var binaryString = window.atob(base64);
    var binaryLen = binaryString.length;
    var bytes = new Uint8Array(binaryLen);

    for (var i = 0; i < binaryLen; i++) {
      var ascii = binaryString.charCodeAt(i);
      bytes[i] = ascii;
    }

    return bytes;
  }

  saveByteArray(reportName, byte) {
    var blob = new Blob([byte], {
      type: "application/pdf"
    });
    var link = document.createElement("a");
    link.href = window.URL.createObjectURL(blob);
    var fileName = reportName;
    link.download = fileName;
    link.click();
  }

}
customElements.define("sakai-rubric-pdf", SakaiRubricPdf);
