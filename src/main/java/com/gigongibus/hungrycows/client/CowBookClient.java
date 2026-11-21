package com.gigongibus.hungrycows.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

// Лише на стороні гравця: розкладає рядки по сторінках і відкриває звичайну книгу Майнкрафта
public final class CowBookClient {
    private static final int PAGE_WIDTH = 114;     // ширина тексту сторінки книги (пікселі)
    private static final int LINES_PER_PAGE = 14;  // скільки рядків вміщує сторінка

    private CowBookClient() {
    }

    public static void open(List<String> lines) {
        StringSplitter splitter = Minecraft.getInstance().font.getSplitter();
        List<FormattedText> pages = new ArrayList<>();
        MutableComponent page = null;
        int used = 0;
        for (String line : lines) {
            boolean title = line.startsWith("#");
            String text = title ? line.substring(1) : line;
            List<FormattedText> wrapped = splitter.splitLines(text, PAGE_WIDTH, Style.EMPTY);
            if (wrapped.isEmpty()) wrapped = List.of(FormattedText.of(""));
            // розділ — з нової сторінки; рядок, що не влазить, — теж
            if (page == null || title || used + wrapped.size() > LINES_PER_PAGE) {
                if (page != null) pages.add(page);
                page = Component.empty();
                used = 0;
            }
            for (FormattedText part : wrapped) {
                if (used > 0) page.append("\n");
                MutableComponent c = Component.literal(part.getString());
                if (title) c = c.withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_GREEN);
                page.append(c);
                used++;
            }
        }
        if (page != null) pages.add(page);
        if (pages.isEmpty()) pages.add(Component.empty());

        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() {
                return pages.size();
            }

            @Override
            public FormattedText getPageRaw(int index) {
                return pages.get(index);
            }
        }));
    }
}
