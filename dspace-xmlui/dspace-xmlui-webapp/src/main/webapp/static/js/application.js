$(document).ready(function(){

    // "Enter search keywords..." label to appear on top of search box. This JS handles this gracefully.
    // Input-Prompt-Text: A Better way - http://kyleschaeffer.com/best-practices/input-prompt-text/
    $('input[title][id="ds-global-search-box"][type="text"]').each(function(i){
        $(this).addClass('input-prompt-' + i);
        var promptSpan = $('<span class="input-prompt"/>');
        $(promptSpan).attr('id', 'input-prompt-' + i);
        $(promptSpan).append($(this).attr('title'));
        $(promptSpan).click(function(){
            $(this).hide();
            $('.' + $(this).attr('id')).focus();
        });
        if($(this).val() != ''){
            $(promptSpan).hide();
        }
        $(this).before(promptSpan);
        $(this).focus(function(){
            $('#input-prompt-' + i).hide();
        });
        $(this).blur(function(){
            if($(this).val() == ''){
                $('#input-prompt-' + i).show();
            }
        });
    });
});