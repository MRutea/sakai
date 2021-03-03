/**********************************************************************************
 *
 * Copyright (c) 2021 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sakaiproject.rubrics.controller;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.rubrics.RubricsConfiguration;
import org.sakaiproject.rubrics.logic.model.Criterion;
import org.sakaiproject.rubrics.logic.model.CriterionOutcome;
import org.sakaiproject.rubrics.logic.model.Evaluation;
import org.sakaiproject.rubrics.logic.model.Rating;
import org.sakaiproject.rubrics.logic.model.Rubric;
import org.sakaiproject.rubrics.logic.repository.EvaluationRepository;
import org.sakaiproject.rubrics.logic.repository.RubricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.PreferencesService;

import org.jsoup.Jsoup;

@Slf4j
@BasePathAwareController
@NoArgsConstructor
@AllArgsConstructor
@RequestMapping(value="/")
public class GeneratePdfController {

    @Autowired
    RubricsConfiguration rubricsConfiguration;

    @Autowired
    RepositoryEntityLinks repositoryEntityLinks;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SiteService siteService;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private PreferencesService preferencesService;

    private static Font font = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
    private static Font textFont = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
    private static Font descFont = new Font(Font.FontFamily.HELVETICA, 7, Font.NORMAL);

    @ResponseBody
    @GetMapping(value = "/getPdf")
    public ResponseEntity<byte[]> getPdf(@RequestParam(name = "sourceId") String sourceId)
                throws Exception {
        Rubric sourceRubric = null;
        List<Evaluation> evaluationList = new ArrayList<>();
        String userId = sessionManager.getCurrentSessionUserId();
        final Locale locale = preferencesService.getLocale(userId);

        try {
           sourceRubric = rubricRepository.findById(Long.parseLong(sourceId))
                .orElseGet(() -> rubricsConfiguration.getDefaultLayoutConfiguration(locale.getCountry()).getDefaultRubric());
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        byte[] bytesResult = createPdf(sourceRubric, evaluationList, locale);
        return ResponseEntity.ok().body(bytesResult);
    }

    @ResponseBody
    @GetMapping(value = "/getGradedPdf")
    public ResponseEntity<byte[]> getGradedPdf(@RequestParam(name = "sourceId") String sourceId, @RequestParam("toolId") String toolId,
            @RequestParam("itemId") String itemId, @RequestParam("evaluatedItemId") String evaluatedItemId)
                throws Exception {
        Rubric sourceRubric = null;
        List<Evaluation> evaluationList = new ArrayList<>();
        String userId = sessionManager.getCurrentSessionUserId();
        final Locale locale = preferencesService.getLocale(userId);

        try {
            sourceRubric = rubricRepository.findById(Long.parseLong(sourceId))
                .orElseGet(() -> rubricsConfiguration.getDefaultLayoutConfiguration(locale.getCountry()).getDefaultRubric());
            evaluationList = evaluationRepository.findByToolIdAndAssociationItemIdAndEvaluatedItemId(toolId, itemId, evaluatedItemId);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        byte[] bytesResult = createPdf(sourceRubric, evaluationList, locale);
        return ResponseEntity.ok().body(bytesResult);
    }

    public byte[] createPdf(Rubric sourceRubric, List<Evaluation> evaluationList, Locale locale)
            throws DocumentException, IOException {
        // Count points
        Integer points = Integer.valueOf(0);
        if (null != evaluationList && !evaluationList.isEmpty()) {
            points = evaluationList.stream().flatMap(a -> a.getCriterionOutcomes().stream()).mapToInt(x -> x.getPoints().intValue()).sum();
        }
        // Create pdf document
        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        PdfPTable table = new PdfPTable(1);
        PdfPCell header = new PdfPCell();
        Paragraph paragraph = new Paragraph(messageSource.getMessage("export_rubric_title", new Object[] { sourceRubric.getTitle() + "\n"}, locale), font);
        paragraph.setAlignment(Element.ALIGN_LEFT);
        if (null != sourceRubric.getMetadata()) {
            paragraph.add(messageSource.getMessage("export_rubric_site", new Object[] { getCurrentSiteName(sourceRubric.getMetadata().getOwnerId()) + "\n" }, locale));
        }
        String exportDate = messageSource.getMessage("export_rubric_date", new Object[] { DateFormat.getDateInstance(DateFormat.LONG, locale).format(new Date()) + "\n" }, locale);
        paragraph.add(exportDate);
        header.setBackgroundColor(BaseColor.LIGHT_GRAY);

        if (null != evaluationList && !evaluationList.isEmpty()) {
            paragraph.add(messageSource.getMessage("export_total_points", new Object[] { points }, locale) );
            paragraph.add(Chunk.NEWLINE);
        }
        paragraph.add(Chunk.NEWLINE);
        header.addElement(paragraph);
        table.addCell(header);
        table.completeRow();
        document.add(table);

        List<Criterion> criterionList = sourceRubric.getCriterions();
        PdfPTable CriterionTable = null;

        for (Criterion cri : criterionList) {
            CriterionTable = new PdfPTable(cri.getRatings().size() + 1);
            String titlePoints = messageSource.getMessage("export_rubrics_points", new Object[] { cri.getTitle(), getCriterionPoints(cri, evaluationList) }, locale);
            CriterionTable.addCell(titlePoints);
            List<Rating> ratingList = cri.getRatings();
            for (Rating rating : ratingList) {
                Paragraph ratingsParagraph = new Paragraph("", textFont);
                String ratingPoints = messageSource.getMessage("export_rubrics_points", new Object[] { rating.getTitle(), rating.getPoints() }, locale);
                ratingsParagraph.add(ratingPoints);
                ratingsParagraph.add(Chunk.NEWLINE);
                Paragraph ratingsDesc = new Paragraph("", descFont);
                if (StringUtils.isNotEmpty(rating.getDescription())) {
                    ratingsDesc.add(rating.getDescription() + "\n");
                }
                ratingsParagraph.add(ratingsDesc);
                PdfPCell newCell = new PdfPCell();
                for (Evaluation evaluation : evaluationList) {
                    List<CriterionOutcome> outcomeList = evaluation.getCriterionOutcomes();
                    for (CriterionOutcome outcome : outcomeList) {
                        if (cri.getId().equals(outcome.getCriterionId())
                                && rating.getId().equals(outcome.getSelectedRatingId())) {
                            newCell.setBackgroundColor(BaseColor.LIGHT_GRAY);
                            if (null != outcome.getComments() && !outcome.getComments().isEmpty()) {
                                ratingsParagraph.add(Chunk.NEWLINE);
                                ratingsParagraph.add(messageSource.getMessage("export_comments", new Object[] { Jsoup.parse(outcome.getComments()).text() + "\n" }, locale));
                            }
                        }
                    }
                }
                newCell.addElement(ratingsParagraph);
                CriterionTable.addCell(newCell);
            }
            CriterionTable.completeRow();
            document.add(CriterionTable);
        }
        document.close();
        return out.toByteArray();
    }

    private String getCriterionPoints(Criterion cri, List<Evaluation> evaluationList) {
        if (null == evaluationList) {
            return "";
        }

        Integer points = Integer.valueOf(0);
        List<Rating> ratingList = cri.getRatings();
        for (Rating rating : ratingList) {
            for (Evaluation evaluation : evaluationList) {
                List<CriterionOutcome> outcomeList = evaluation.getCriterionOutcomes();
                for (CriterionOutcome outcome : outcomeList) {
                    if (cri.getId() == outcome.getCriterionId()
                            && rating.getId() == outcome.getSelectedRatingId()) {
                        points = points + outcome.getPoints().intValue();
                    }
                }
            }
        }
        return points.toString();
    }

    public String getCurrentSiteName(String siteId){
        String siteName = "";
        try {
            Site site = siteService.getSite(siteId);
            siteName = site.getTitle();
        } catch (IdUnusedException ex) {
            log.error(ex.getMessage(), ex);
        }
        return siteName;
    }

}
