package br.com.mentoria.validacao.servico;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Le PDFs em memoria: o texto nativo, quando existe, ou a primeira pagina como imagem para o OCR. */
@Service
public class PdfService {

    private static final float DPI_OCR = 300;

    public String extrairTexto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    /** PDF escaneado nao tem texto: a primeira pagina vira PNG em tons de cinza para o Tesseract. */
    public byte[] primeiraPaginaComoImagem(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf); var saida = new ByteArrayOutputStream()) {
            var pagina = new PDFRenderer(documento).renderImageWithDPI(0, DPI_OCR, ImageType.GRAY);
            ImageIO.write(pagina, "png", saida);
            return saida.toByteArray();
        }
    }
}
